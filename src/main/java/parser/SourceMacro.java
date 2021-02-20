/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.CodeStatement;
import java.util.HashMap;
import parser.dialects.SjasmDialect;


public class SourceMacro {
    public String name = null;
    public CodeStatement definingStatement = null;
    public List<String> argNames = new ArrayList<>();
    public boolean variableNumberofArgs = false;
    public List<Expression> defaultValues = new ArrayList<>();
    // line + lineNumber:
    public List<SourceLine> lines = new ArrayList<>();
    public List<SourceLine> elseLines = new ArrayList<>();  // only used by IF-ELSE-ENDIF macro

    // predefined macro arguments/state:
    public List<Expression> preDefinedMacroArgs = null;
    public boolean insideElse = false;

    // in ASDZ80, if you define an argument preceded by a "?", it is not an argument, but a mark to
    // define a temporary local label:
    public List<String> temporaryLabelArgNames = new ArrayList<>();    

    public SourceMacro(String a_name, CodeStatement a_ds)
    {
        name = a_name;
        definingStatement = a_ds;
    }


    public SourceMacro(String a_name, List<String> a_args, List<Expression> a_defaultValues, CodeStatement a_ds, MDLConfig config)
    {        
        name = a_name;

        for(String argName:a_args) {
            if (config.preProcessor.temporaryLabelArgPrefix != null &&
                argName.startsWith(config.preProcessor.temporaryLabelArgPrefix)) {
                argName = argName.substring(config.preProcessor.temporaryLabelArgPrefix.length());
                temporaryLabelArgNames.add(argName);
            } else {
                argNames.add(argName);
            }
        }
        defaultValues = a_defaultValues;
        definingStatement = a_ds;
        
        // check for macros with numeric arguments:
        if (argNames.size() == 1 && Tokenizer.isInteger(argNames.get(0))) {
            // macro with numeric arguments!
            int nargs = Integer.parseInt(argNames.get(0));
            argNames.clear();
            for(int i = 0;i<nargs;i++) {
                argNames.add("@" + (i+1));
            }
        } else if (argNames.size() == 2 && argNames.get(0).endsWith("..") && argNames.get(1).equals("*")) {
            // macro with a variable number of arguments:
            argNames.clear();
            variableNumberofArgs = true;
        }
    }


    public void addLine(SourceLine sl)
    {
        if (insideElse) {
            elseLines.add(sl);
        } else {
            lines.add(sl);
        }
    }


    public MacroExpansion instantiate(List<Expression> args, CodeStatement macroCall, CodeBase code, MDLConfig config)
    {        
        List<SourceLine> lines2 = new ArrayList<>();
        MacroExpansion me = new MacroExpansion(this, macroCall, lines2);
        if (config.preProcessor.isMacroName(name, config.preProcessor.MACRO_REPT)) {
            Integer reptNRepetitions_value = args.get(0).evaluateToInteger(macroCall, code, false);
            if (reptNRepetitions_value == null) {
                config.error("Could not evaluate REPT argument " + args.get(0));
                return null;
            }
            String counterName = null;
            List<String> reptArgNames = new ArrayList<>();
            if (args.size()>=2 && args.get(1).type == Expression.EXPRESSION_SYMBOL) {
                counterName = args.get(1).symbolName;
                reptArgNames.add(counterName);
            }
                        
            String scope = null;
            if (macroCall.label != null) {
                scope = macroCall.label.name;
            } else {
                // check if the label was in the previous statement:
                CodeStatement previous = macroCall.source.getPreviousStatementTo(macroCall, code);
                if (previous.source == macroCall.source && previous.label != null && previous.type == CodeStatement.STATEMENT_NONE) {
                    scope = previous.label.name;
                } else {
                    scope = config.preProcessor.nextMacroExpansionContextName(macroCall.labelPrefix);
                }
            }
            for(int i = 0;i<reptNRepetitions_value;i++) {
                List<Expression> reptArgs = new ArrayList<>();
                reptArgs.add(Expression.constantExpression(i, config));
                List<SourceLine> linesTmp = new ArrayList<>();
                for(SourceLine sl:lines) {
                    // we create new instances, as we will modify them:
                    if (counterName != null) {
                        SourceLine sl2 = replaceMacroArgs(sl, reptArgNames, reptArgs, null, macroCall, config);
                        linesTmp.add(sl2);
                    } else {
                        linesTmp.add(new SourceLine(sl.line, sl.source, sl.lineNumber));
                    }
                }
                if (scope != null) {
                    lines2.add(new SourceLine(scope + "." + i + ":", macroCall.sl.source, macroCall.sl.lineNumber));
                }
                scopeMacroExpansionLines(scope+"." + i, linesTmp, code, config);
                lines2.addAll(linesTmp);
            }
        } else if (config.preProcessor.isMacroName(name, config.preProcessor.MACRO_IF)) {
            Integer ifCondition_value = args.get(0).evaluateToInteger(macroCall, code, false);
            if (ifCondition_value == null) {
                config.error("Could not evaluate IF argument " + args.get(0) + ": " + macroCall.sl);
                args.get(0).evaluateToInteger(macroCall, code, false);
                return null;
            }
            if (ifCondition_value == Expression.FALSE) {
                lines2.addAll(elseLines);
            } else {
                lines2.addAll(lines);
            }
        } else if (config.preProcessor.isMacroName(name, config.preProcessor.MACRO_IFDEF)) {
            Expression exp = args.get(0);
            boolean defined = false;
            if (exp.type == Expression.EXPRESSION_SYMBOL) {
                SourceConstant sc = code.getSymbol(exp.symbolName);
                if (sc != null && sc.exp != null) defined = true;
                if (!defined && config.preProcessor.isMacro(exp.symbolName)) defined = true;
                if (!defined && config.preProcessor.textMacros.containsKey(exp.symbolName)) defined = true;
            } else {
                config.error("Incorrect parameter to " + config.preProcessor.MACRO_IFDEF + ": " + args.get(0));
                return null;
            }
            if (defined) {
                lines2.addAll(lines);
            } else {
                lines2.addAll(elseLines);
            }
            
        } else if (config.preProcessor.isMacroName(name, config.preProcessor.MACRO_IFNDEF)) {
            Expression exp = args.get(0);
            boolean defined = false;
            if (exp.type == Expression.EXPRESSION_SYMBOL) {
                SourceConstant sc = code.getSymbol(exp.symbolName);
                if (sc != null && sc.exp != null) defined = true;
                if (!defined && config.preProcessor.isMacro(exp.symbolName)) defined = true;
                if (!defined && config.preProcessor.textMacros.containsKey(exp.symbolName)) defined = true;
            } else {
                config.error("Incorrect parameter to " + config.preProcessor.MACRO_IFDEF + ": " + args.get(0));
                return null;
            }
            if (defined) {
                lines2.addAll(elseLines);
            } else {
                lines2.addAll(lines);
            }
            
        } else if (config.preProcessor.dialectMacros.containsKey(name)) {
            return config.dialectParser.instantiateMacro(this, args, macroCall, code);
        } else {
            // instantiate arguments:
            if (!variableNumberofArgs) {
                while(args.size() < argNames.size()) {
                    Expression defaultValue = defaultValues.get(args.size());
                    if (defaultValue == null) {
                        config.error("Number of parameters in macro call incorrect!");
                        return null;
                    } else {
                        args.add(defaultValue);
                    }
                }
                
                HashMap<String, String> temporaryLabelsMap = new HashMap<>();
                for(String label:temporaryLabelArgNames) {
                    if (config.dialectParser != null) {
                        String newLabel = config.dialectParser.getNextTemporaryLabel();
                        if (newLabel != null) {
                            System.out.println("newLabel: " + newLabel + " with labelPrefix: " + macroCall.labelPrefix);
                            temporaryLabelsMap.put(label, newLabel);
                        }
                    }
                }

                for(SourceLine sl:lines) {
                    SourceLine sl2 = replaceMacroArgs(sl, argNames, args, temporaryLabelsMap, macroCall, config);
                    lines2.add(sl2);
                }                
            } else {
                // this can only happen in sjasm:
                if (config.dialectParser instanceof SjasmDialect) {
                    lines2.addAll(((SjasmDialect)config.dialectParser).expandVariableNumberOfArgsMacro(args, macroCall, this, code, config));
                } else {
                    config.error("macro with a variable number of patterns in a dialect different than sjasm!");
                    return null;
                }
            }

            // rename all the macro-defined, labels with the new scope:
            String scope;
            if (macroCall.label != null) {
                scope = macroCall.label.name;
            } else {
                CodeStatement previous = macroCall.source.getPreviousStatementTo(macroCall, code);
                if (previous.source == macroCall.source && previous.label != null && previous.type == CodeStatement.STATEMENT_NONE) {
                    scope = previous.label.name;
                } else {
                    scope = config.preProcessor.nextMacroExpansionContextName(macroCall.labelPrefix);
                }
            }

            scopeMacroExpansionLines(scope, lines2, code, config);
        }

        return me;
    }

    
    public SourceLine replaceMacroArgs(SourceLine sl, List<String> names, List<Expression> args, 
                                       HashMap<String, String> temporaryLabelsMap, 
                                       CodeStatement macroCall, MDLConfig config)
    {
        String line2 = sl.line;
        List<String> tokens = Tokenizer.tokenizeIncludingBlanks(line2);
        line2 = "";

        String previous = null;
        for(String token:tokens) {
            if (previous != null && config.lineParser.macroArguentPrefixes.contains(previous) && Tokenizer.isSymbol(token)) {
                // variable name starting with "?" (or equivalent parameter prefix for the dialect):
                line2 = line2.substring(0, line2.length()-1);
                token = previous + token;
            }
            String newToken = token;
            if (temporaryLabelsMap != null && temporaryLabelsMap.containsKey(token)) {
                newToken = temporaryLabelsMap.get(token);
            } else {
                for(int i = 0;i<names.size();i++) {
                    if (token.equals(names.get(i))) {
                        // we wrap it spaces, to prevent funny interaction of tokens, e.g., two "-" in a row forming a "--":
                        newToken = " " + args.get(i).toString() + " ";
                    } else {
                        // special case for Glass when we have something like "?parametername.field":
                        if (names.get(i).startsWith("?") && token.startsWith(names.get(i)) &&
                            token.charAt(names.get(i).length()) == '.') {
                            newToken = " " + args.get(i).toString() + token.substring(names.get(i).length());
                        }
                    }
                }
            }
            line2 += newToken;
            previous = token;
        }
        return new SourceLine(line2, sl.source, sl.lineNumber, macroCall);
    }

    
    public void scopeMacroExpansionLines(String scope, List<SourceLine> lines, CodeBase code, MDLConfig config)
    {
        if (!lines.isEmpty()) {
            lines.get(0).labelPrefixToPush = scope + ".";
            
            SourceLine l2 = new SourceLine("", lines.get(0).source, lines.get(0).lineNumber);
            l2.labelPrefixToPop = scope + ".";            
            lines.add(l2);
        }
    }
}

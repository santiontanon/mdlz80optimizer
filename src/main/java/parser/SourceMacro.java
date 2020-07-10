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
import code.SourceFile;
import code.SourceStatement;


public class SourceMacro {
    public String name = null;
    public SourceStatement definingStatement = null;
    public List<String> argNames = new ArrayList<>();
    public List<Expression> defaultValues = new ArrayList<>();
    // line + lineNumber:
    List<SourceLine> lines = new ArrayList<>();
    List<SourceLine> elseLines = new ArrayList<>();  // only used by IF-ELSE-ENDIF macro

    // predefined macro arguments/state:
    public List<Expression> preDefinedMacroArgs = null;
    public boolean insideElse = false;


    public SourceMacro(String a_name, SourceStatement a_ds)
    {
        name = a_name;
        definingStatement = a_ds;
    }


    public SourceMacro(String a_name, List<String> a_args, List<Expression> a_defaultValues, SourceStatement a_ds)
    {
        name = a_name;
        argNames = a_args;
        defaultValues = a_defaultValues;
        definingStatement = a_ds;
    }


    public void addLine(String line, SourceFile f, Integer lineNumber)
    {
        if (insideElse) {
            elseLines.add(new SourceLine(line, f, lineNumber));
        } else {
            lines.add(new SourceLine(line, f, lineNumber));
        }
    }


    public MacroExpansion instantiate(List<Expression> args, SourceStatement macroCall, CodeBase code, MDLConfig config)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        MacroExpansion me = new MacroExpansion(this, macroCall, lines2);
        if (config.preProcessor.isMacroName(name, config.preProcessor.MACRO_REPT)) {
            Integer reptNRepetitions_value = args.get(0).evaluate(macroCall, code, false);
            if (reptNRepetitions_value == null) {
                config.error("Could not evaluate REPT argument " + args.get(0));
                return null;
            }
            String scope;
            if (macroCall.label != null) {
                scope = macroCall.label.name;
            } else {
                scope = config.preProcessor.nextMacroExpansionContextName();
            }
            for(int i = 0;i<reptNRepetitions_value;i++) {
                List<SourceLine> linesTmp = new ArrayList<>();
                for(SourceLine sl:lines) {
                    // we create new instances, as we will modify them:
                    linesTmp.add(new SourceLine(sl.line, sl.source, sl.lineNumber));
                }
                scopeMacroExpansionLines(scope+"."+i, linesTmp, code, config);
                lines2.addAll(linesTmp);
            }
        } else if (config.preProcessor.isMacroName(name, config.preProcessor.MACRO_IF)) {
            Integer ifCondition_value = args.get(0).evaluate(macroCall, code, false);
            if (ifCondition_value == null) {
                config.error("Could not evaluate IF argument " + args.get(0) + ": " + macroCall);
                args.get(0).evaluate(macroCall, code, false);
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
            } else {
                config.error("Incorrect parameter to " + config.preProcessor.MACRO_IFDEF + ": " + args.get(0));
                return null;
            }
            if (defined) {
                lines2.addAll(lines);
            } else {
                lines2.addAll(elseLines);
            }
        } else {
            // instantiate arguments:
            while(args.size() < argNames.size()) {
                Expression defaultValue = defaultValues.get(args.size());
                if (defaultValue == null) {
                    config.error("Number of parameters in macro call incorrect!");
                    return null;
                } else {
                    args.add(defaultValue);
                }
            }

            for(SourceLine sl:lines) {
                String line2 = sl.line;
                for(int i = 0;i<argNames.size();i++) {
                    line2 = line2.replace("?" + argNames.get(i),
                                          args.get(i).toString());
                }
                lines2.add(new SourceLine(line2, sl.source, sl.lineNumber));
            }

            // rename all the macro-defined, labels with the new scope:
            String scope;
            if (macroCall.label != null) {
                scope = macroCall.label.name;
            } else {
                scope = config.preProcessor.nextMacroExpansionContextName();
            }

            scopeMacroExpansionLines(scope, lines2, code, config);
        }

        return me;
    }


    public void scopeMacroExpansionLines(String scope, List<SourceLine> lines, CodeBase code, MDLConfig config)
    {
        List<String> macroDefinedLabels = new ArrayList<>();
        for(SourceLine sl:lines) {
            // Find if the macro defined a label, to properly scope it:
            SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, sl.source, sl.lineNumber, null);
            List<String> tokens = Tokenizer.tokenize(sl.line);
            if (tokens != null) {
                config.lineParser.parseLabel(tokens, sl.line, sl.lineNumber, s, sl.source, code, false);
            }
            if (s.label != null) {
                macroDefinedLabels.add(s.label.name);
            }
        }
        // System.out.println("Expanding " + this.name + " with scope " + scope + " and defined labels: " + macroDefinedLabels);

        for(SourceLine sl:lines) {
            for(String definedLabel:macroDefinedLabels) {
                List<String> tokens = Tokenizer.tokenize(sl.line);
                if (!tokens.isEmpty()) {
                    for(int i = 0;i<tokens.size();i++) {
                        String token = tokens.get(i);
                        String token2 = config.lineParser.getLabelPrefix() + token;
                        if (token.equals(definedLabel)) {
                            tokens.set(i, scope + "." + definedLabel);
                        } else if (token2.equals(definedLabel)) {
                            tokens.set(i, scope + "." + definedLabel);
                        } else if (token.startsWith(definedLabel + ".")) {
                            tokens.set(i, scope + "." + definedLabel + token.substring(definedLabel.length()));
                        } else if (token2.startsWith(definedLabel + ".")) {
                            tokens.set(i, scope + "." + definedLabel + token2.substring(definedLabel.length()));
                        }
                    }
                    String reconstructedLine = sl.line.startsWith(tokens.get(0)) ? "":"  ";
                    for(String token:tokens) reconstructedLine += token + " ";
                    sl.line = reconstructedLine;
                }
            }
        }
    }
}

/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.util.HashMap;
import org.apache.commons.lang3.tuple.Pair;
import parser.MacroExpansion;
import parser.PreProcessor;
import parser.SourceLine;
import parser.SourceMacro;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class GlassDialect implements Dialect {
    MDLConfig config;
    List<String> sectionStack = new ArrayList<>();
    
    // Although this is not documented, it seems you can have the same "section XXX" command multiple times
    // in the same codebase, and the address counters just continue from the last time. So, we need to keep
    // track of how many times each section has appeared:
    HashMap<String,Integer> sectionAppearanceCounters = new HashMap<>();
    
    // We keep track, to give a warning at the end, since Section is not fully supported yet if MDL is asked to generate output assembler
    boolean usedSectionKeyword = false;
    
    public GlassDialect(MDLConfig a_config)
    {
        config = a_config;

        config.eagerMacroEvaluation = false;  // Glass expects lazy evaluation of macros
        
        config.preProcessor.dialectMacros.put("irp", "endm");
    }


    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("error")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("warning")) return true;
        
        return false;
    }


    @Override
    public String newSymbolName(String name, Expression value) {
        if (name.equalsIgnoreCase("org") ||
            name.equalsIgnoreCase("db") ||
            name.equalsIgnoreCase("dw") ||
            name.equalsIgnoreCase("dd") ||
            name.equalsIgnoreCase("ds") ||
            name.equalsIgnoreCase("macro") ||
            name.equalsIgnoreCase("endm") ||
            name.equalsIgnoreCase("rept") ||
            name.equalsIgnoreCase("if") ||
            name.equalsIgnoreCase("else") ||
            name.equalsIgnoreCase("endif")) {
            return null;
        }
        return name;
    }


    @Override
    public String symbolName(String name)
    {
        return name;
    }

    
    String getSectionName(String baseName, CodeBase code)
    {
        SourceConstant sc = code.getSymbol(baseName);
        if (sc == null) return baseName;
        while(sc.exp != null && sc.exp.type == Expression.EXPRESSION_SYMBOL && !sc.exp.symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
            SourceConstant sc2 = code.getSymbol(sc.exp.symbolName);
            if (sc2 == null) break;
            sc = sc2;
        }
        return sc.name;
    }
    

    @Override
    public List<SourceStatement> parseLine(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) {
            usedSectionKeyword = true;
            
            tokens.remove(0);

            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl);
                return null;
            }
            if (exp.type != Expression.EXPRESSION_SYMBOL) {
                config.error("Invalid section name at " + sl);
                return null;
            }
            String sectionName = getSectionName(exp.symbolName, code);
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            
            int appearanceCounter = 1;
            if (sectionAppearanceCounters.containsKey(sectionName)) {
                appearanceCounter = sectionAppearanceCounters.get(sectionName);
            }

            sectionStack.add(0, sectionName + "_" + appearanceCounter);

            // it's not the first time we have seen this section:
            if (appearanceCounter > 1) {
                s.org = Expression.symbolExpression("__address_before_section_"+sectionName+"_"+(appearanceCounter-1)+"_ends", code, config);
            } 
            
            // Insert a helper statement, so we can restore the address counter after the section is complete:
            SourceStatement sectionHelper = new SourceStatement(SourceStatement.STATEMENT_NONE, sl, source, null);
            sectionHelper.label = new SourceConstant("__address_before_section_"+sectionName+"_"+appearanceCounter+"_starts", null, 
                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config), sectionHelper);
            code.addSymbol(sectionHelper.label.name, sectionHelper.label);
            l.add(0, sectionHelper);

            sectionAppearanceCounters.put(sectionName, appearanceCounter+1);
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) {
            if (!sectionStack.isEmpty()) {
                tokens.remove(0);
                String sectionName_appearanceCounter = sectionStack.remove(0);
//                int appearanceCounter = sectionAppearanceCounters.get(sectionName);
                
                // Insert two helper statements, so we can restore the address counter after the section is complete, and continue the section later on
                SourceStatement sectionHelper1 = new SourceStatement(SourceStatement.STATEMENT_NONE, sl, source, null);
                sectionHelper1.label = new SourceConstant("__address_before_section_"+sectionName_appearanceCounter+"_ends", null, 
                        Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config), sectionHelper1);
                code.addSymbol(sectionHelper1.label.name, sectionHelper1.label);
                SourceStatement sectionHelper2 = new SourceStatement(SourceStatement.STATEMENT_ORG, sl, source, null);
                sectionHelper2.org = Expression.symbolExpression("__address_before_section_"+sectionName_appearanceCounter+"_starts", code, config);
                l.add(0,sectionHelper1);
                l.add(1,sectionHelper2);
//                sectionAppearanceCounters.put(sectionName, appearanceCounter+1);
                
                if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            } else {
                config.error("No section to terminate at " + sl);
                return null;
            }
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) {
            if (s.label == null) {
                config.error("Proc with no name at " + sl);
                return null;
            }
            tokens.remove(0);
            config.lineParser.pushLabelPrefix(s.label.name + ".");
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) {
            tokens.remove(0);
            config.lineParser.popLabelPrefix();
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("error")) {
            config.error(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("warning")) {
            config.warn(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        return null;
    }


    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code)
    {
        // Attempt to assemble the macro content at address 0, and define all the internal symbols as macroname.symbol:
        // To do that, I instantiate the macro with all the parameters that do not have defaults taking the value 0:
        // However, it is not always possible to do this, so, this is only attempted, and if it fails
        // no compilation happens:
        List<Expression> args = new ArrayList<>();
        for(int i=0;i<macro.argNames.size();i++) {
            if (macro.defaultValues.get(i) != null) {
                args.add(macro.defaultValues.get(i));
            } else {
                args.add(Expression.constantExpression(0, config));
            }
        }

        // Assemble the macro at address 0:
        boolean succeeded = true;
        try {
            // supress error messages when attempting to assemble a macro, as it might fail:
            config.logger.silence();
            MacroExpansion expansion = macro.instantiate(args, macro.definingStatement, code, config);
            List<SourceLine> lines = expansion.lines;
            PreProcessor preProcessor = new PreProcessor(config.preProcessor);

            SourceFile f = new SourceFile(macro.definingStatement.source.fileName + ":macro(" + macro.name+")", null, null, config);
            int lineNumber = macro.definingStatement.sl.lineNumber;
            while(true) {
                List<String> tokens = new ArrayList<>();
                Pair<SourceLine, Integer> tmp = getNextLine(lines, f, lineNumber, tokens, preProcessor);
                if (tmp == null) {
                    if (config.preProcessor.withinMacroDefinition()) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    } else {
                        config.debug("Glass: successfully assembled macro " + macro.name);
                    }
                    break;
                }
                lineNumber = tmp.getRight();
                SourceLine sl = tmp.getLeft();
                
                if (preProcessor.withinMacroDefinition()) {
                    List<SourceStatement> newStatements = preProcessor.parseMacroLine(tokens, sl, f, code, config);
                    if (newStatements == null) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    } else {
                        for(SourceStatement s:newStatements) {
                            f.addStatement(s);
                        }
                    }
                } else {
                    List<SourceStatement> l = config.lineParser.parse(Tokenizer.tokenize(sl.line), 
                            sl, f, code, config);
                    if (l == null) {
                        // we fail to assemble the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    }
                    for(SourceStatement s:l) {
                        if (!s.isEmpty()) {
                            if (!preProcessor.handleStatement(sl, s, f, code, false)) {
                                f.addStatement(s);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // we fail to evaluate the macro, but it's ok, some times it can happen
            succeeded = false;
        }
        config.logger.resume();

        // this is a debug message, not a warning, as it can definitively happen if macros contain unresolved symbols:
        if (!succeeded) config.debug("Glass: failed to assemble macro " + macro.name);

        return true;
    }
    
    
    // Returns: <SourceLine, file_linenumber>
    Pair<SourceLine, Integer> getNextLine(List<SourceLine> lines, SourceFile f, int file_linenumber, List<String> tokens, PreProcessor preProcessor)
    {
        List<String> unfilteredTokens = new ArrayList<>();
        
        SourceLine sl = preProcessor.expandMacros();
        if (sl == null && !lines.isEmpty()) {
            sl = lines.remove(0); // ignore the line numbers here
            file_linenumber++;
        }
        if (sl == null) return null;
        
        Tokenizer.tokenize(sl.line, unfilteredTokens);
        if (!unfilteredTokens.isEmpty() && unfilteredTokens.get(unfilteredTokens.size()-1).equals(",")) {
            // unfinished line, get the next one!
            List<String> tokens2 = new ArrayList<>();
            Pair<SourceLine, Integer> tmp = getNextLine(lines, f, file_linenumber, tokens2, preProcessor);
            if (tmp != null) {
                sl = new SourceLine(sl.line += "\n" + tmp.getLeft().line, sl.source, sl.lineNumber);
                unfilteredTokens.addAll(tokens2);
                file_linenumber = tmp.getRight();
            }
        }
        
        // Glass does not support multi-line comments, so, no need to handle that here as in SourceCodeParser.getNextLine
        tokens.addAll(unfilteredTokens);
        
        return Pair.of(sl, file_linenumber);
    }
    
    
    @Override
    public MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, SourceStatement macroCall, CodeBase code)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        MacroExpansion me = new MacroExpansion(macro, macroCall, lines2);
        
        if (macro.name.equals("irp")) {
            if (args.isEmpty()) return null;
            if (args.get(0).type != Expression.EXPRESSION_SYMBOL) {
                config.error("First parameter to IRP should be a variable name");
                return null;
            }
            String variableName = args.get(0).symbolName;            
            String scope;
            if (macroCall.label != null) {
                scope = macroCall.label.name;
            } else {
                scope = config.preProcessor.nextMacroExpansionContextName();
            }
            for(int i = 1;i<args.size();i++) {
                List<SourceLine> linesTmp = new ArrayList<>();
                for(SourceLine sl:macro.lines) {
                    // we create new instances, as we will modify them:
                    linesTmp.add(new SourceLine(sl.line, sl.source, sl.lineNumber));
                }
                macro.scopeMacroExpansionLines(scope+"."+i, linesTmp, code, config);
                for(SourceLine sl:linesTmp) {
                    String line2 = sl.line;
                    line2 = line2.replace(variableName, args.get(i).toString());
                    lines2.add(new SourceLine(line2, sl.source, sl.lineNumber));
                }
            }
            return me;
        } else {
            return null;
        }
    }

    
    @Override
    public void performAnyFinalActions(CodeBase code)
    {
        if (usedSectionKeyword) {
            config.warn("Glass's 'section' keyword was used. If you are asking MDL to generate assembler output, the result might not be compilable, as 'section' requires re-organizing the input lines, which is currently not done.");
        }
    }    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.io.File;
import java.util.HashMap;
import java.util.StringTokenizer;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class SjasmDialect implements Dialect {

    MDLConfig config;

    String lastAbsoluteLabel = null;
    SourceFile structFile = null;
    int structStart = 0;

    int mapCounter = 0;
    List<Integer> mapCounterStack = new ArrayList<>();
    
    HashMap<Integer,Expression> pageStart = new HashMap<>();
    HashMap<Integer,Expression> pageSize = new HashMap<>();
    
    HashMap<String, Integer> reusableLabelCounts = new HashMap<>();

    
    public SjasmDialect(MDLConfig a_config) {
        config = a_config;

        config.warningJpHlWithParenthesis = false;  // I don't think sjasm supports "jp hl"
        
        config.lineParser.addKeywordSynonym("byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("dword", config.lineParser.KEYWORD_DD);
        config.lineParser.addKeywordSynonym("=", config.lineParser.KEYWORD_EQU);
        
        config.preProcessor.macroSynonyms.put("endmacro", config.preProcessor.MACRO_ENDM);
        
        config.warningJpHlWithParenthesis = false;
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.keywordsHintingALabel.add("#");
        config.lineParser.keywordsHintingALabel.add("field");
        config.lineParser.keywordsHintingALabel.add(":=");
        config.lineParser.allowIncludesWithoutQuotes = true;
        config.lineParser.macroNameIsFirstArgumentOfMacro = true;
        config.lineParser.allowNumberLabels = true;
        
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("high");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("low");
        
        // We define it as a dialectMacro instead of as a synonym of "REPT", as it has some special syntax for
        // indicating the current iteration
        config.preProcessor.dialectMacros.put("repeat", "endrepeat");
    }

    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("struct")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("map")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmap")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("#")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("field")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("incdir")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defpage")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("code")) return true;
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("[")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(":=")) return true;
        return false;
    }

    @Override
    public String newSymbolName(String name, Expression value) {
        if (name.equalsIgnoreCase("struct")
                || name.equalsIgnoreCase("ends")
                || name.equalsIgnoreCase("byte")
                || name.equalsIgnoreCase("defb")
                || name.equalsIgnoreCase("word")
                || name.equalsIgnoreCase("defw")
                || name.equalsIgnoreCase("dword")
                || name.equalsIgnoreCase("map")
                || name.equalsIgnoreCase("endmap")
                || name.equalsIgnoreCase("field")
                || name.equalsIgnoreCase("assert")
                || name.equalsIgnoreCase("incdir")
                || name.equalsIgnoreCase("output")
                || name.equalsIgnoreCase("defpage")
                || name.equalsIgnoreCase("code")) {
            return null;
        }
        if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else if (Tokenizer.isInteger(name)) {
            // it's a reusable label:
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            reusableLabelCounts.put(name, count+1);
            return "_sjasm_reusable_" + name + "_" + count;
        } else {
            // When a name has "CURRENT_ADDRESS" as its value, it means it's a label.
            // If it does not start by ".", then it's an absolute label:
            if (value != null &&
                value.type == Expression.EXPRESSION_SYMBOL &&
                value.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
                lastAbsoluteLabel = name;
            }
        }

        return name;
    }

    
    @Override
    public String symbolName(String name) {
        if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else if ((name.endsWith("f") || name.endsWith("F")) && Tokenizer.isInteger(name.substring(0, name.length()-1))) {
            // it's a reusable label:
            name = name.substring(0, name.length()-1);
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            return "_sjasm_reusable_" + name + "_" + count;
        } else if ((name.endsWith("b") || name.endsWith("B")) && Tokenizer.isInteger(name.substring(0, name.length()-1))) {
            // it's a reusable label:
            name = name.substring(0, name.length()-1);
            int count = reusableLabelCounts.get(name);
            return "_sjasm_reusable_" + name + "_" + (count-1);
        } else {
            return name;
        }
    }

    
    @Override
    public List<SourceStatement> parseLine(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("struct")) {
            tokens.remove(0);
            config.lineParser.pushLabelPrefix(tokens.remove(0) + ".");
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("struc")) {
                // TODO(santi@): investigate what is this syntax, I found it in this file: https://github.com/GuillianSeed/MetalGear/blob/master/constants/structures.asm
                // But it does not seem to be documented, I think it might be an error that sjasm just happens to swalow
                tokens.remove(0);
            }
            structFile = source;
            structStart = source.getStatements().size();
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) {
            tokens.remove(0);
            if (structFile == null) {
                config.error("ends outside of a struct at " + sl);
                return null;
            }
            if (structFile != source) {
                config.error("struct split among multiple files is not supported at " + sl);
                return null;
            }

            // Transform the struct into equ definitions with local labels:
            int offset = 0;
            for (int i = structStart; i < source.getStatements().size(); i++) {
                SourceStatement s2 = source.getStatements().get(i);
                int offset_prev = offset;
                switch (s2.type) {
                    case SourceStatement.STATEMENT_NONE:
                        break;
                    case SourceStatement.STATEMENT_DATA_BYTES:
                    case SourceStatement.STATEMENT_DATA_WORDS:
                    case SourceStatement.STATEMENT_DATA_DOUBLE_WORDS:
                        offset += s2.sizeInBytes(code, true, true, true);
                        break;
                    default:
                        config.error("Unsupported statement (type="+s2.type+") inside a struct definition at " + sl);
                        return null;
                }
                if (s2.label != null) {
                    s2.type = SourceStatement.STATEMENT_CONSTANT;
                    s2.label.exp = Expression.constantExpression(offset_prev, config);
                } else {
                    s2.type = SourceStatement.STATEMENT_NONE;
                }
            }

            config.lineParser.popLabelPrefix();
            structFile = null;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) {
            tokens.remove(0);
            // just ignore
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("map")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            mapCounterStack.add(0, mapCounter);
            mapCounter = exp.evaluate(s, code, false);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmap")) {
            tokens.remove(0);
            mapCounter = mapCounterStack.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 &&
            (tokens.get(0).equalsIgnoreCase("#") || tokens.get(0).equalsIgnoreCase("field"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            if (s.label == null) {
                config.error("Field expression does not have a label at " + sl);
                return null;
            }
            s.label.exp = exp;
            mapCounter += exp.evaluate(s, code, false);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            Integer value = exp.evaluate(s, code, false);
            if (value == null || value == Expression.FALSE) {
                config.error("Assertion failed at " + sl);
                return null;
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("incdir")) {
            tokens.remove(0);
            String folder = "";
            while(!tokens.isEmpty()) {
                if (Tokenizer.isSingleLineComment(tokens.get(0)) || 
                    Tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                folder += tokens.remove(0);
            }

            // Make sure we don't have a windows/Unix path separator problem:
            if (folder.contains("\\")) folder = folder.replace("\\", File.separator);
            
            File path = new File(config.lineParser.pathConcat(source.getPath(), folder));
            config.includeDirectories.add(path);
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) {
            // Just ignore ...
            while(!tokens.isEmpty()) {
                if (Tokenizer.isSingleLineComment(tokens.get(0)) || 
                    Tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                tokens.remove(0);
            }
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defpage")) {
            tokens.remove(0);
            Expression pageExp = config.expressionParser.parse(tokens, s, code);
            Expression pageStartExp = null;
            Expression pageSizeExp = null;
            if (pageExp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                pageStartExp = config.expressionParser.parse(tokens, s, code);
                if (pageStartExp == null) {
                    config.error("Cannot parse expression at " + sl);
                    return null;
                }
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                pageSizeExp = config.expressionParser.parse(tokens, s, code);
                if (pageSizeExp == null) {
                    config.error("Cannot parse expression at " + sl);
                    return null;
                }
            }
            if (pageStartExp != null) pageStart.put(pageExp.evaluate(s, code, false), pageStartExp);
            if (pageSizeExp != null) pageStart.put(pageExp.evaluate(s, code, false), pageSizeExp);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("code")) {
            tokens.remove(0);
            Expression addressExp = null;
            if (!tokens.isEmpty() && tokens.get(0).equals("?")) {
                config.error("Unsupported form of code keyword at " + sl);
                return null;
            }                    
            if (!tokens.isEmpty() && tokens.get(0).equals("@")) {
                tokens.remove(0);
                addressExp = config.expressionParser.parse(tokens, s, code);
                if (addressExp == null) {
                    config.error("Cannot parse expression at " + sl);
                    return null;
                }
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) tokens.remove(0);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals("#")) {
                config.error("Unsupported form of code keyword at " + sl);
                return null;
            }
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("page")) {
                tokens.remove(0);
                Expression pageExp = config.expressionParser.parse(tokens, s, code);
                if (addressExp == null) {
                    int page = pageExp.evaluate(s, code, false);
                    addressExp = pageStart.get(page);
                    if (addressExp == null) {
                        config.error("Undefined page at " + sl);
                        return null;
                    }
                } else {
                    // ignore
                }
            }
            if (addressExp != null) {
                // parse it as an "org"
                s.type = SourceStatement.STATEMENT_ORG;
                s.org = addressExp;                
            } else {
                // ignore
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("[")) {
            tokens.remove(0);
            Expression numberExp = config.expressionParser.parse(tokens, s, code);
            if (tokens.isEmpty() || !tokens.get(0).equals("]")) {
                config.error("Cannot parse line at " + sl);
                return null;
            }
            tokens.remove(0);
            int number = numberExp.evaluate(s, code, false);
            List<SourceStatement> l2 = config.lineParser.parse(tokens, sl, source, code, config);
            if (l2 == null) {
                config.error("Cannot parse line at " + sl);
                return null;
            }
            l.clear();
            for(int i = 0;i<number;i++) {
                l.addAll(l2);
            }
            return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(":=")) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode at " + sl);
                return null;
            }
            
            tokens.remove(0);
            s.label.resolveEagerly = true;
            if (!config.lineParser.parseEqu(tokens, sl, s, source, code)) return null;
            s.label.clearCache();
            Integer value = s.label.exp.evaluate(s, code, false);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return null;
            }
            s.label.exp = Expression.constantExpression(value, config);
            
            // these variables should not be part of the source code:
            l.clear();
            return l;
        }
        
        return null;
    }


    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code) {
        return true;
    }
    
    
    @Override
    public Integer evaluateExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code, boolean silent)
    {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            Integer value = args.get(0).evaluate(s, code, silent);
            if (value == null) return null;
            return (value >> 8)&0xff;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            Integer value = args.get(0).evaluate(s, code, silent);
            if (value == null) return null;
            return value&0xff;
        }
        return null;
    }

    
    @Override
    public MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, SourceStatement macroCall, CodeBase code)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        MacroExpansion me = new MacroExpansion(macro, macroCall, lines2);
        
        if (macro.name.equals("repeat")) {
            if (args.isEmpty()) return null;
            int iterations = args.get(0).evaluate(macroCall, code, false);
//            String scope;
//            if (macroCall.label != null) {
//                scope = macroCall.label.name;
//            } else {
//                scope = config.preProcessor.nextMacroExpansionContextName();
//            }
            for(int i = 0;i<iterations;i++) {
                String variable = "@#";
                List<SourceLine> linesTmp = new ArrayList<>();
                for(SourceLine sl:macro.lines) {
                    // we create new instances, as we will modify them:
                    linesTmp.add(new SourceLine(sl.line, sl.source, sl.lineNumber));
                }
                // macro.scopeMacroExpansionLines(scope+"."+i, linesTmp, code, config);
                for(SourceLine sl:linesTmp) {
                    String line2 = sl.line;
                    StringTokenizer st = new StringTokenizer(line2, " \t");
                    if (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        if (token.equalsIgnoreCase("repeat")) {
                            variable = "@" + variable;
                        } else if (token.equalsIgnoreCase("endrepeat")) {
                            variable = variable.substring(1);
                        }
                    }
                    line2 = line2.replace(variable, i + "");
//                    System.out.println("line2 ("+variable+"): " + line2);
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
        config.warn("Use of sjasm reusable labels, which are conductive to human error.");
    }
    
}

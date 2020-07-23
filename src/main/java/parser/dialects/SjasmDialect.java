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
import code.SourceConstant;
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
    
    public static class SjasmStruct {
        String name;
        SourceFile file;
        SourceStatement start;
        List<String> attributeNames = new ArrayList<>();
        List<Integer> attributeSizes = new ArrayList<>();
    }
    

    MDLConfig config;

    String lastAbsoluteLabel = null;
    SjasmStruct struct = null;
    List<SjasmStruct> structs = new ArrayList<>();

    int mapCounter = 0;
    List<Integer> mapCounterStack = new ArrayList<>();
    
    Integer currentPage = null;
    HashMap<Integer,Expression> pageStart = new HashMap<>();
    HashMap<Integer,Expression> pageSize = new HashMap<>();
    HashMap<String,Integer> symbolPage = new HashMap<>();
    
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
        config.lineParser.allowExtendedSjasmInstructions = true;
        
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("high");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("low");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add(":");
        
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
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("code")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) return true;
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("[")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(":=")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) return true;
        for(SjasmStruct s:structs) {
            if (tokens.get(0).equals(s.name)) return true;
        }
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
            name = lastAbsoluteLabel + "." + name.substring(1);
        } else if (Tokenizer.isInteger(name)) {
            // it's a reusable label:
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            reusableLabelCounts.put(name, count+1);
            name =  "_sjasm_reusable_" + name + "_" + count;
        } else {
            // When a name has "CURRENT_ADDRESS" as its value, it means it's a label.
            // If it does not start by ".", then it's an absolute label:
            if (value != null &&
                value.type == Expression.EXPRESSION_SYMBOL &&
                value.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
                lastAbsoluteLabel = name;
            }
        }
        
        symbolPage.put(name, currentPage);

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
            struct = new SjasmStruct();
            struct.name = tokens.remove(0);
            struct.file = source;
            config.lineParser.pushLabelPrefix(struct.name + ".");
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("struc")) {
                // TODO(santi@): investigate what is this syntax, I found it in this file: https://github.com/GuillianSeed/MetalGear/blob/master/constants/structures.asm
                // But it does not seem to be documented, I think it might be an error that sjasm just happens to swalow
                tokens.remove(0);
            }
            struct.file = source;
            struct.start = s;
            s.type = SourceStatement.STATEMENT_CONSTANT;
            SourceConstant c = new SourceConstant(struct.name, null, null, s);
            s.label = c;
            if (!code.addSymbol(c.name, c)) return null;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) {
            tokens.remove(0);
            if (struct.file == null) {
                config.error("ends outside of a struct at " + sl);
                return null;
            }
            if (struct.file != source) {
                config.error("struct split among multiple files is not supported at " + sl);
                return null;
            }
            
            // Transform the struct into equ definitions with local labels:
            int offset = 0;
            int start = source.getStatements().indexOf(struct.start) + 1;
            for (int i = start; i < source.getStatements().size(); i++) {
                SourceStatement s2 = source.getStatements().get(i);
                int offset_prev = offset;
                switch (s2.type) {
                    case SourceStatement.STATEMENT_NONE:
                        break;
                    case SourceStatement.STATEMENT_DATA_BYTES:
                    case SourceStatement.STATEMENT_DATA_WORDS:
                    case SourceStatement.STATEMENT_DATA_DOUBLE_WORDS:
                    {
                        int size = s2.sizeInBytes(code, true, true, true);
                        offset += size;
                        if (s2.label != null) {
                            struct.attributeNames.add(s2.label.name);
                        } else {
                            struct.attributeNames.add(null);
                        }
                        struct.attributeSizes.add(size);
                        break;
                    }
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

            // Record the struct for later:
//            System.out.println("New struct definition: " + struct.name + ":\n  - " + struct.attributeNames + "\n  - " + struct.attributeSizes);
            struct.start.label.exp = Expression.constantExpression(offset, config);
            structs.add(struct);
            config.lineParser.keywordsHintingALabel.add(struct.name);
            config.lineParser.popLabelPrefix();
            struct = null;
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
            mapCounter = exp.evaluateToInteger(s, code, false);
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
            if (struct != null) {
                s.label.exp = exp;
            } else {
                s.label.exp = Expression.constantExpression(mapCounter, config);
            }
            s.type = SourceStatement.STATEMENT_CONSTANT;
            mapCounter += exp.evaluateToInteger(s, code, false);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            Integer value = exp.evaluateToInteger(s, code, false);
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
            if (pageStartExp != null) {
                pageStart.put(pageExp.evaluateToInteger(s, code, false), pageStartExp);
            }
            if (pageSizeExp != null) pageSize.put(pageExp.evaluateToInteger(s, code, false), pageSizeExp);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("code")) {
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
                    int page = pageExp.evaluateToInteger(s, code, false);
                    currentPage = page;
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
                l.remove(s);
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) {
            tokens.remove(0);
            Expression pageExp = null;
            pageExp = config.expressionParser.parse(tokens, s, code);
            if (pageExp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            int page = pageExp.evaluateToInteger(s, code, false);
            currentPage = page;
            Expression addressExp = pageStart.get(page);
            if (addressExp == null) {
                config.error("Undefined page at " + sl);
                return null;
            }
            // parse it as an "org"
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = addressExp;                
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
            int number = numberExp.evaluateToInteger(s, code, false);
            l.clear();
            for(int i = 0;i<number;i++) {
                List<String> tokensCopy = new ArrayList<>();
                tokensCopy.addAll(tokens);
                // we need to parse it every time, to create multiple different copies of the statements:
                List<SourceStatement> l2 = config.lineParser.parse(tokensCopy, sl, source, code, config);
                if (l2 == null) {
                    config.error("Cannot parse line at " + sl);
                    return null;
                }

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
            Integer value = s.label.exp.evaluateToInteger(s, code, false);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return null;
            }
            s.label.exp = Expression.constantExpression(value, config);
            
            // these variables should not be part of the source code:
            l.clear();
            return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) {
            tokens.remove(0);
            if (!config.lineParser.parseData(tokens, "db", sl, s, source, code)) return null;
            // insert a "0" at the end of each string:
            List<Expression> newData = new ArrayList<>();
            for(Expression exp:s.data) {
                newData.add(exp);
                newData.add(Expression.constantExpression(0, config));
            }
            s.data = newData;
            return l;
        }        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return null;
            }
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            // ds virtual (((($-1)/exp)+1)*exp-$)
            s.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                        Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                          Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                              Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                                Expression.parenthesisExpression(
                                  Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                                      Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config), 
                                      Expression.constantExpression(1, config), config), config),
                                  exp, config), 
                              Expression.constantExpression(1, config), config), config), 
                          exp, config),
                        Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config), config);
            return l;
        }   
        
        // struct definitions:
        for(SjasmStruct st:structs) {
            if (tokens.get(0).equals(st.name)) {
                tokens.remove(0);
                // it is a struct definition:
                boolean done = false;
                List<Expression> data = new ArrayList<>();
                while (!done) {
                    Expression exp = config.expressionParser.parse(tokens, s, code);
                    if (exp == null) {
                        config.error("Cannot parse line " + sl);
                        return null;
                    } else {
                        data.add(exp);
                    }
                    if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                        tokens.remove(0);
                    } else {
                        done = true;
                    }
                }
                if (data.size() != st.attributeSizes.size()) {
                    config.error("Struct instantiation has the wrong number of fields ("+data.size()+" vs the expected "+st.attributeSizes.size()+") in " + sl);
                    return null;                    
                }
                l.clear();
                
                for(int i = 0;i<data.size();i++) {
                    SourceStatement s2;
                    switch(st.attributeSizes.get(i)) {
                        case 1:
                            s2 = new SourceStatement(SourceStatement.STATEMENT_DATA_BYTES, sl, source, null);
                            break;
                        case 2:
                            s2 = new SourceStatement(SourceStatement.STATEMENT_DATA_WORDS, sl, source, null);
                            break;
                        case 4:
                            s2 = new SourceStatement(SourceStatement.STATEMENT_DATA_DOUBLE_WORDS, sl, source, null);
                            break;
                        default:
                            config.error("Field " + st.attributeNames.get(i) + " of struct " + st.name + " has an unsupported size in: " + sl);
                            return null;
                    }
                    if (i == 0) s2.label = s.label;
                    s2.data = new ArrayList<>();
                    s2.data.add(data.get(i));
                    l.add(s2);
                }
                if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
                break;
            }
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
            Integer value = args.get(0).evaluateToInteger(s, code, silent);
            if (value == null) return null;
            return (value >> 8)&0xff;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            Integer value = args.get(0).evaluateToInteger(s, code, silent);
            if (value == null) return null;
            return value&0xff;
        }
        if (functionName.equalsIgnoreCase(":") && args.size() == 1) {
            if (args.get(0).type != Expression.EXPRESSION_SYMBOL) {
                config.error("':' operator used on a non-symbol expression at " + s.sl);
                return null;
            }
            Integer page = symbolPage.get(args.get(0).symbolName);
            if (page == null) {
                config.error("Unknown page of symbol "+args.get(0).symbolName+" at " + s.sl);
                return null;
            }
            return page;
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
            int iterations = args.get(0).evaluateToInteger(macroCall, code, false);
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
        if (reusableLabelCounts.size() > 0) {
            config.warn("Use of sjasm reusable labels, which are conductive to human error.");
        }
    }
    
    
    // Sjasm is an eager-evaluated assembler, which clashes with the lazy 
    // evaluation done by MDL. So, in order to capture the "rotate" syntax used
    // in sjasm when used together with variable number of argument macros in
    // order to have a similar functionality as Glass' IRP macro, we cover 
    // here a basic common case (if needed, more cases will be covered in the 
    // future, but this syntax is very problematic given the way macros are
    // resolved in MDL...):    
    public List<SourceLine> expandVariableNumberOfArgsMacro(List<Expression> args, SourceStatement macroCall, SourceMacro macro, CodeBase code, MDLConfig config)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        
        List<String> argNames = new ArrayList<>();
        for(int i = 0;i<args.size()+1;i++) {
            argNames.add("@" + i);
        }
        // first argument is the number of args:
        args.add(0, Expression.constantExpression(args.size(), config));
                
        List<SourceLine> repeatLines = null;
        List<SourceLine> repeatLinesToExecute = null;
        
        for(SourceLine sl:macro.lines) {
            String line2 = sl.line;
            List<String> tokens = Tokenizer.tokenizeIncludingBlanks(line2);
            line2 = "";

            boolean allEmptySoFar = true;
            for(String token:tokens) {
                if (allEmptySoFar) {
                    if (token.equalsIgnoreCase("repeat")) {
                        if (repeatLines != null) {
                            config.error("nested repeats in variable argument macros not yet supported in " + sl);
                            return null;
                        }
                        repeatLines = new ArrayList<>();
                    } else if (token.equalsIgnoreCase("endrepeat")) {
                        if (repeatLines == null) {
                            config.error("mismatched endrepeat in " + sl);
                            return null;
                        }
                        repeatLinesToExecute = repeatLines;
                        repeatLines = null;
                    }
                }
                
                if (!token.trim().equals("")) allEmptySoFar = false;
                
                if (repeatLines == null || repeatLines.isEmpty()) {
                    String newToken = token;
                    for(int i = 0;i<argNames.size();i++) {
                        if (token.equals(argNames.get(i))) {
                            // we wrap it spaces, to prevent funny interaction of tokens, e.g., two "-" in a row forming a "--":
                            newToken = " " + args.get(i).toString() + " ";
                        }
                    }
                    line2 += newToken;
                } else {
                    line2 += token;
                }
            }
            
            if (repeatLinesToExecute != null) {
                SourceLine repeatStatement = repeatLinesToExecute.remove(0);
                List<String> tokens2 = Tokenizer.tokenize(repeatStatement.line);
                tokens2.remove(0);  // skip "repeat"
                Expression exp = config.expressionParser.parse(tokens2, macroCall, code);
                int nIterations = exp.evaluateToInteger(macroCall, code, false);
                for(int i = 0;i<nIterations;i++) {
                    for(SourceLine sl3:repeatLinesToExecute) {
                        List<String> tokens3 = Tokenizer.tokenizeIncludingBlanks(sl3.line);
                        String line3 = "";
                        for(String token:tokens3) {
                            String newToken = token;
                            for(int j = 0;j<argNames.size();j++) {
                                if (token.equals(argNames.get(j))) {
                                    newToken = args.get(j).toString();
                                }
                            }
                            line3 += newToken;
                        }
                        
                        if (line3.trim().toLowerCase().startsWith("rotate ")) {
                            // execute a rotate:
                            List<String> tokensRotate = Tokenizer.tokenize(line3);
                            tokensRotate.remove(0); // skip "rotate"
                            Expression expRotate = config.expressionParser.parse(tokensRotate, macroCall, code);
                            int nRotations = expRotate.evaluateToInteger(macroCall, code, false);
                            while(nRotations>0) {
                                Expression argExp = args.remove(1);
                                args.add(argExp);
                                nRotations--;
                            }
                            while(nRotations<0) {
                                Expression argExp = args.remove(args.size()-1);
                                args.add(0, argExp);
                                nRotations++;
                            }
                        } else {                            
                            lines2.add(new SourceLine(line3, sl3.source, sl3.lineNumber, macroCall));
                        }
                    }
                }
                repeatLinesToExecute = null;
            } else if (repeatLines == null) {
                lines2.add(new SourceLine(line2, sl.source, sl.lineNumber, macroCall));
            } else {
                repeatLines.add(new SourceLine(line2, sl.source, sl.lineNumber, macroCall));
            }
        }   

        return lines2;
    }
}

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
import parser.SourceLine;
import parser.SourceMacro;

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

    public SjasmDialect(MDLConfig a_config) {
        config = a_config;

        config.warningJpHlWithParenthesis = false;  // I don't think sjasm supports "jp hl"
        
        config.lineParser.addKeywordSynonym("byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("dword", config.lineParser.KEYWORD_DD);

        config.warningJpHlWithParenthesis = false;
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.keywordsHintingALabel.add("#");
        config.lineParser.keywordsHintingALabel.add("field");
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
                || name.equalsIgnoreCase("end")
                || name.equalsIgnoreCase("map")
                || name.equalsIgnoreCase("endmap")
                || name.equalsIgnoreCase("field")
                || name.equalsIgnoreCase("assert")) {
            return null;
        }
        if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
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

        return null;
    }


    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code) {
        return true;
    }
    
    
    @Override
    public Integer evaluateExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code, boolean silent)
    {
        return null;
    }


    @Override
    public void performAnyFinalActions(CodeBase code)
    {
        
    }
    
}

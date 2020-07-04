/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;
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
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
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
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) {
            tokens.remove(0);
            if (structFile == null) {
                config.error("ends outside of a struct at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
            }
            if (structFile != source) {
                config.error("struct split among multiple files is not supported at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
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
                        config.error("Unsupported statement (type="+s2.type+") inside a struct definition at " + source.fileName + ", "
                                + lineNumber + ": " + line);
                        return false;
                }
                if (s2.label != null) {
                    s2.type = SourceStatement.STATEMENT_CONSTANT;
                    s2.label.exp = Expression.constantExpression(offset_prev);
                } else {
                    s2.type = SourceStatement.STATEMENT_NONE;
                }
            }

            config.lineParser.popLabelPrefix();
            structFile = null;
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) {
            tokens.remove(0);
            // just ignore
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("map")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;                
            }
            mapCounterStack.add(0, mapCounter);
            mapCounter = exp.evaluate(s, code, false);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmap")) {
            tokens.remove(0);
            mapCounter = mapCounterStack.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size() >= 2 && 
            (tokens.get(0).equalsIgnoreCase("#") || tokens.get(0).equalsIgnoreCase("field"))) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;                
            }
            if (s.label == null) {
                config.error("Field expression does not have a label at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;                
            }
            s.label.exp = exp;
            mapCounter += exp.evaluate(s, code, false);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;                
            }
            Integer value = exp.evaluate(s, code, false);
            if (value == null || value == Expression.FALSE) {
                config.error("Assertion failed at " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;                
            }
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        
        return false;
    }

    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code) {
        return true;
    }
}

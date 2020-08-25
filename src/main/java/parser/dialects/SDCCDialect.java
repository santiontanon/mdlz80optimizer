/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import parser.SourceLine;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class SDCCDialect implements Dialect {

    MDLConfig config;
    
    
    List<String> definedAreas = new ArrayList<>();
    String currentArea;
    
    
    public SDCCDialect(MDLConfig a_config)
    {
        config = a_config;

        // We ignore the distinction between ":" and "::" for now:
        config.lineParser.addKeywordSynonym("::", config.lineParser.KEYWORD_COLON);

        config.lineParser.KEYWORD_EQU = ".equ";
        config.lineParser.addKeywordSynonym(".gblequ", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym(".lclequ", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym("=", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym("==", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym("=:", config.lineParser.KEYWORD_EQU);

        config.lineParser.KEYWORD_DS = ".ds";
        config.lineParser.addKeywordSynonym(".blkb", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym(".rmb", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym(".rs", config.lineParser.KEYWORD_DS);

        config.lineParser.KEYWORD_DB = ".db";
        config.lineParser.addKeywordSynonym(".byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".fcb", config.lineParser.KEYWORD_DB);

        config.lineParser.KEYWORD_DW = ".dw";
        config.lineParser.addKeywordSynonym(".word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym(".fdb", config.lineParser.KEYWORD_DW);
        
        config.lineParser.KEYWORD_ORG = ".org";
        
        config.expressionParser.dialectFunctions.add("<");        
        config.expressionParser.dialectFunctions.add(">");

        config.expressionParser.sdccStyleHashMarksForConstants = true;
        config.lineParser.sdccStyleOffsets = true;
        
        // Note, these have to be deactivated as soon as we are done parsing (otherwise, the optimizer patterns will not be parsed right)
        Tokenizer.sdccStyleDollarInLabels = true;
        Tokenizer.sdccStyleHashMarksForConstants = true;
        config.hexStyle = MDLConfig.HEX_STYLE_0X;
    }    
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".module")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".optsdcc")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".globl")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".area")) return true;
        return false;
    }
    
    
    private boolean isLocalLabelName(String label)
    {
        if (label.endsWith("$") &&
            label.charAt(0)>='0' &&
            label.charAt(0)<='9') return true;
        return false;
    }
    
    
    private String getLastAbsoluteLabel(SourceStatement s) 
    {
        // sjasm considers any label as an absolute label, even if it's associated with an equ,
        // so, no need to check if s.label.isLabel() (as in asMSX):
        while(s != null) {
            if (s.label != null && !isLocalLabelName(s.label.originalName)) {
                return s.label.originalName;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
    
    
    @Override
    public String newSymbolName(String name, Expression value, SourceStatement s) {        
        // A relative label
        if (isLocalLabelName(name)) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name.substring(0, name.length()-1);
            }
        }

        // An absolute label
        return name;
    }


    @Override
    public String symbolName(String name, SourceStatement s) {
        // A relative label
        if (isLocalLabelName(name)) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name.substring(0, name.length()-1);
            }
        }

        // An absolute label
        return name;
    }
    
    
    @Override
    public List<SourceStatement> parseLine(List<String> tokens, SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".module")) {
            tokens.remove(0);
            tokens.remove(0);   // module name
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".optsdcc")) {
            tokens.remove(0);
            while(!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".globl")) {
            tokens.remove(0);
            tokens.remove(0);   // label name
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".area")) {
            tokens.remove(0);
            String areaName = tokens.remove(0);   // label name
            if (tokens.size()>=3) {
                if (tokens.get(0).equals("(") && tokens.get(2).equals(")")) {
                    // (ABS)
                    tokens.remove(0);
                    tokens.remove(0);
                    tokens.remove(0);
                }
            }
            currentArea = areaName;
            if (!definedAreas.contains(areaName)) {
                definedAreas.add(areaName);
                Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);
                String symbolName = newSymbolName("s_" + areaName, exp, s);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + symbolName + " in " + sl);
                    return null;
                }
                SourceConstant c = new SourceConstant(symbolName, "s_" + areaName, exp, s, config);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (code.addSymbol(c.name, c) != 1) {
                    return null;
                }
            }            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }

        return null;
    }        

    
    // Some dialect functions can be translated to standard expressions. This is preferable than direct evaluation, 
    // if possible, since expressions that contain labels might change value during optimization:
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code)
    {
        // lower byte:
        if (functionName.equalsIgnoreCase("<") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, false, true, config), config);
        }
        // higher byte:
        if (functionName.equalsIgnoreCase(">") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, false, true, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
        }
        
        return null;
    }
    

    @Override
    public boolean performAnyFinalActions(CodeBase code)
    {            
        Tokenizer.sdccStyleDollarInLabels = false;
        Tokenizer.sdccStyleHashMarksForConstants = false;
        
        return true;
    }
    
    
    @Override
    public String statementToString(SourceStatement s, Path rootPath) {
        switch(s.type) {
            case SourceStatement.STATEMENT_CPUOP:
            {
                String str = s.toStringLabel() + "    ";
                str += s.op.spec.opName;
                if (config.output_opsInLowerCase) str = str.toLowerCase();

                for(int i = 0;i<s.op.args.size();i++) {
                    if (i==0) {
                        str += " ";
                    } else {
                        str += ", ";
                    }
                    if (s.op.args.get(i).evaluatesToIntegerConstant() &&
                        (!s.op.isJump() && !s.op.isCall())) {
                        if (i == 0 && s.op.args.size()>1) {
                            // no mark on left-hand side indirections:
                            str += s.op.args.get(i).toString();
                        } else {
                            // mark the value as a constant:
                            if (s.op.args.get(i).type == Expression.EXPRESSION_PARENTHESIS) {
                                str += "(#" + s.op.args.get(i).args.get(0) + ")";
                            } else {
                                if (s.op.args.get(i).args != null && s.op.args.get(i).args.size()>1) {
                                    str += "#(" + s.op.args.get(i).toString() + ")";
                                } else {
                                    str += "#" + s.op.args.get(i).toString();
                                }
                            }
                        }
                    } else {
                        str += s.op.args.get(i).toString();
                    }
                }
                
                return str;
            }
            case SourceStatement.STATEMENT_DATA_BYTES:
                {
                    String str = s.toStringLabel() + "    ";
                    str += "    .byte ";
                    for(int i = 0;i<s.data.size();i++) {
                        str += s.data.get(i).toStringInternal(true);
                        if (i != s.data.size()-1) {
                            str += ", ";
                        }
                    }
                    return str;
                }
            case SourceStatement.STATEMENT_DATA_WORDS:
                {
                    String str = s.toStringLabel() + "    ";
                    str += "    .word ";
                    for(int i = 0;i<s.data.size();i++) {
                        str += s.data.get(i).toString();
                        if (i != s.data.size()-1) {
                            str += ", ";
                        }
                    }
                    return str;
                }
            
            default:
                return s.toStringUsingRootPath(rootPath);
        }
    }
        
}

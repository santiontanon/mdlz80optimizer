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
import code.CodeStatement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parser.LineParser;
import parser.SourceLine;

/**
 *
 * @author santi
 * 
 * Dialect for the asz80 assembler, used by SDCC: https://shop-pdp.net/ashtml/asmlnk.htm
 * 
 */
public class SDCCDialect implements Dialect {

    MDLConfig config;
    
    // Some lines do not make sense in standard zilog assembler, and MDL removes them,
    // but if we want to generate assembler targetting SDCC/SDASZ80, we need those lines.
    // Examples are the ".area" or ".globl" statements
    List<SourceLine> linesToKeepIfGeneratingDialectAsm = new ArrayList<>(); 
            
    HashMap<String, SourceLine> definedAreas = new HashMap<>();
    String currentArea;
    
    int nextTemporaryLabel = 10000;
    
    
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
        config.lineParser.addKeywordSynonym(".ascii", config.lineParser.KEYWORD_DB);

        config.lineParser.KEYWORD_DW = ".dw";
        config.lineParser.addKeywordSynonym(".word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym(".fdb", config.lineParser.KEYWORD_DW);
        
        config.lineParser.KEYWORD_ORG = ".org";
        
        config.preProcessor.MACRO_MACRO = ".macro";
        config.preProcessor.MACRO_ENDM = ".endm";
        config.preProcessor.MACRO_REPT = ".rept";
        config.preProcessor.temporaryLabelArgPrefix = "?";
        
        config.lineParser.macroDefinitionStyle = LineParser.MACRO_MACRO_NAME_ARGS;
        
        config.expressionParser.dialectFunctions.add("<");        
        config.expressionParser.dialectFunctions.add(">");

        config.expressionParser.sdccStyleHashMarksForConstants = true;
        config.lineParser.sdccStyleOffsets = true;
        
        // Note, these have to be deactivated as soon as we are done parsing (otherwise, the optimizer patterns will not be parsed right)
        config.tokenizer.sdccStyleDollarInLabels = true;
        config.tokenizer.sdccStyleHashMarksForConstants = true;
        config.hexStyle = MDLConfig.HEX_STYLE_0X;
        config.labelsHaveSafeValues = false;

        config.ignorePatternsWithTags.add(MDLConfig.SDCC_UNSAFE_TAG);
        
        config.lineParser.resetKeywordsHintingALabel();
    }    
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code) {
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
    
    
    private SourceConstant getLastAbsoluteLabel(CodeStatement s) 
    {
        // sjasm considers any label as an absolute label, even if it's associated with an equ,
        // so, no need to check if s.label.isLabel() (as in asMSX):
        while(s != null) {
            if (s.label != null && !isLocalLabelName(s.label.originalName)) {
                return s.label;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
    
    
    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement s) {        
        // A relative label
        if (isLocalLabelName(name)) {
            SourceConstant lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.originalName + name.substring(0, name.length()-1), lastAbsoluteLabel);
            }
        }

        // An absolute label
        return Pair.of(config.lineParser.getLabelPrefix() + name, null);
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement s) {
        // A relative label
        if (isLocalLabelName(name)) {
            SourceConstant lastAbsoluteLabel = getLastAbsoluteLabel(s);
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.originalName+ name.substring(0, name.length()-1), lastAbsoluteLabel);
            }
        }

        // An absolute label
        return Pair.of(name, null);
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) 
    {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".module")) {
            linesToKeepIfGeneratingDialectAsm.add(sl);
            tokens.remove(0);
            tokens.remove(0);   // module name
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".optsdcc")) {
            linesToKeepIfGeneratingDialectAsm.add(sl);
            tokens.remove(0);
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".globl")) {
            linesToKeepIfGeneratingDialectAsm.add(sl);
            tokens.remove(0);
            tokens.remove(0);   // label name
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".area")) {
            linesToKeepIfGeneratingDialectAsm.add(sl);
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
            if (!definedAreas.containsKey(areaName)) {
                definedAreas.put(areaName, sl);
                Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);
                Pair<String, SourceConstant> tmp = newSymbolName("s_" + areaName, exp, s);
                if (tmp == null) {
                    config.error("Problem defining symbol " + "s_" + areaName + " in " + sl);
                    return false;
                }
                String symbolName = tmp.getLeft();
                SourceConstant c = new SourceConstant(symbolName, "s_" + areaName, exp, s, config);
                s.type = CodeStatement.STATEMENT_NONE;
                s.label = c;
                if (code.addSymbol(c.name, c) != 1) {
                    return false;
                }
            }            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        return false;
    }        

    
    // Some dialect functions can be translated to standard expressions. This is preferable than direct evaluation, 
    // if possible, since expressions that contain labels might change value during optimization:
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code)
    {
        // lower byte:
        if (functionName.equalsIgnoreCase("<") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, Expression.RENDER_AS_16BITHEX, config), config);
        }
        // higher byte:
        if (functionName.equalsIgnoreCase(">") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, Expression.RENDER_AS_16BITHEX, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
        }
        
        return null;
    }
    

    @Override
    public boolean postParseActions(CodeBase code)
    {            
        config.tokenizer.sdccStyleDollarInLabels = false;
        config.tokenizer.sdccStyleHashMarksForConstants = false;
        
        return true;
    }
    
    
    String renderExpressionWithConstantMark(Expression exp, boolean useOriginalNames, CodeBase code)
    {
        // mark the value as a constant:
        if (exp.evaluatesToStringConstant()) {
            return exp.toStringInternal(true, useOriginalNames, true, code);
        } else if (exp.type == Expression.EXPRESSION_PARENTHESIS) {
            return "(#" + exp.args.get(0) + ")";
        } else {
            if (exp.args != null && exp.args.size()>1) {
                return "#(" + exp.toStringInternal(true, useOriginalNames, true, code) + ")";
            } else {
                return "#" + exp.toStringInternal(true, useOriginalNames, true, code);
            }
        }
    }
    
    
    Expression makeExpressionSdasz80Friendly(Expression exp)
    {
        if (exp.type == Expression.EXPRESSION_BITAND &&
            exp.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            exp.args.get(1).integerConstant == 0xff) {
            List<Expression> args = new ArrayList<>();
            args.add(exp.args.get(0));
            return Expression.dialectFunctionExpression("<", args, config);
        } 
        if (exp.type == Expression.EXPRESSION_RSHIFT &&
            exp.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            exp.args.get(1).integerConstant == 8 &&
            exp.args.get(0).type == Expression.EXPRESSION_PARENTHESIS &&
            exp.args.get(0).args.get(0).type == Expression.EXPRESSION_BITAND &&
            exp.args.get(0).args.get(0).args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            exp.args.get(0).args.get(0).args.get(1).integerConstant == 0xff00) {
            List<Expression> args = new ArrayList<>();
            args.add(exp.args.get(0).args.get(0).args.get(0));
            return Expression.dialectFunctionExpression(">", args, config);
        } 
        return exp;
    }
    
    
    public String toStringLabelWithoutSafetyEqu(CodeStatement s, boolean useOriginalNames)
    {
        boolean tmp = config.output_safetyEquDollar;
        config.output_safetyEquDollar = false;
        String str = s.toStringLabel(useOriginalNames, true);
        config.output_safetyEquDollar = tmp;
        return str;
    }
    
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code, boolean useOriginalNames, Path rootPath) {
        switch(s.type) {
            case CodeStatement.STATEMENT_NONE:
                if (linesToKeepIfGeneratingDialectAsm.contains(s.sl)) {
                    return s.sl.line;
                } else {
                    String str = toStringLabelWithoutSafetyEqu(s, useOriginalNames);
                    if (s.comment != null) str += "  " + s.comment;
                    return str;
                }

            case CodeStatement.STATEMENT_ORG:
            {
                String str = toStringLabelWithoutSafetyEqu(s, useOriginalNames);
                str += "    "+config.lineParser.KEYWORD_ORG+" " + s.org.toString();
                return str;
            }
            
            case CodeStatement.STATEMENT_CPUOP:
            {
                String str = toStringLabelWithoutSafetyEqu(s, useOriginalNames) + "    ";
                
                // sdasz80 does not like expressinos of the type: (label & 0xff),
                // so, we translate them to its own syntax using the "<(...)" and ">(...)" operators:
                List<Expression> args = new ArrayList<>();
                for(Expression arg:s.op.args) {
                    args.add(makeExpressionSdasz80Friendly(arg));
                }

                boolean official = true;
                for(Expression arg:args) {
                    if (arg.type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                        (arg.registerOrFlagName.equals("ixl") ||
                         arg.registerOrFlagName.equals("ixh") ||
                         arg.registerOrFlagName.equals("iyl") ||
                         arg.registerOrFlagName.equals("iyh"))) {
                        official = false;
                        break;
                    }
                }
                if (!official) {
                    // sdasz80 does not support unofficial instructions, 
                    // so we need to encode them as bytes:
                    List<Integer> bytes = s.op.assembleToBytes(s, code, config);
                    str += ".byte ";
                    for(int i = 0;i<bytes.size();i++) {
                        str += bytes.get(i);
                        if (i != bytes.size()-1) {
                            str += ", ";
                        }
                    }                    
                    return str + "  ; mdl direct byte sequence equivalent of unofficial: " + s.op.toString();
                }
                
                str += s.op.spec.opName;
                if (config.output_opsInLowerCase) str = str.toLowerCase();
                
                for(int i = 0;i<args.size();i++) {
                    if (i==0) {
                        str += " ";
                    } else {
                        str += ", ";
                    }
                    if (args.get(i).evaluatesToIntegerConstant() &&
                        (!s.op.isJump() && !s.op.isCall())) {
                        if (i == 0 && args.size()>1 && 
                            !s.op.spec.opName.equalsIgnoreCase("in") &&
                            !s.op.spec.opName.equalsIgnoreCase("out")) {
                            // no mark on left-hand side indirections (except in/out):
                            str += args.get(i).toStringInternal(true, useOriginalNames, true, code);
                        } else {
                            // mark the value as a constant:
                            str += renderExpressionWithConstantMark(args.get(i), useOriginalNames, code);
                        }
                    } else if (s.op.spec.args.get(i).regOffsetIndirection != null) {
                        // write "inc -3 (ix)" instead of "ld (ix + -3), a"
                        if (args.get(i).type == Expression.EXPRESSION_PARENTHESIS) {
                            Expression exp = args.get(i).args.get(0);
                            switch (exp.type) {
                                case Expression.EXPRESSION_SUM:
                                    if (exp.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                                        str += exp.args.get(1).toString() + " " + "(" + exp.args.get(0).toStringInternal(true, useOriginalNames, true, code) + ")";
                                    } else {
                                        str += exp.args.get(0).toString() + " " + "(" + exp.args.get(1).toStringInternal(true, useOriginalNames, true, code) + ")";
                                    }   break;
                                case Expression.EXPRESSION_SUB:
                                    if (exp.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                                        Expression aux = Expression.operatorExpression(Expression.EXPRESSION_SIGN_CHANGE, exp.args.get(1), config);
                                        str += aux.toString() + " " + "(" + exp.args.get(0).toStringInternal(true, useOriginalNames, true, code) + ")";
                                    } else {
                                        
                                    }   break;
                                case Expression.EXPRESSION_REGISTER_OR_FLAG:
                                    str += "0 " + "(" + exp.args.get(0).toStringInternal(true, useOriginalNames, true, code) + ")";
                                    break;
                                default:
                                    str += args.get(i).toStringInternal(true, useOriginalNames, true, code);
                                    break;
                            }
                        } else {
                            str += args.get(i).toStringInternal(true, useOriginalNames, true, code);
                        }
                    } else {
                        str += args.get(i).toStringInternal(true, useOriginalNames, true, code);
                    }
                }
                
                if (s.comment != null) str += "  " + s.comment; 
                
                return str;
            }
            
            case CodeStatement.STATEMENT_CONSTANT:
            {
                boolean tmp = config.output_equsWithoutColon;
                // sdasz80 does not like colons in equs:
                config.output_equsWithoutColon = true;
                String str = toStringLabelWithoutSafetyEqu(s, useOriginalNames) + " ";
                config.output_equsWithoutColon = tmp;
                str += config.lineParser.KEYWORD_EQU+" " + s.label.exp.toString();
                return str;
            }   
            
            case CodeStatement.STATEMENT_DATA_BYTES:
            {
                String str = toStringLabelWithoutSafetyEqu(s, useOriginalNames) + "    ";
                if (s.data.size() == 1 && s.data.get(0).evaluatesToStringConstant()) {
                    str += ".ascii ";                        
                } else {
                    str += ".byte ";
                }
                for(int i = 0;i<s.data.size();i++) {
                    str += renderExpressionWithConstantMark(s.data.get(i), useOriginalNames, code);
                    if (i != s.data.size()-1) {
                        str += ", ";
                    }
                }
                if (s.comment != null) str += "  " + s.comment; 
                return str;
            }

            case CodeStatement.STATEMENT_DATA_WORDS:
            {
                String str = toStringLabelWithoutSafetyEqu(s, useOriginalNames) + "    ";
                str += ".word ";
                for(int i = 0;i<s.data.size();i++) {
                    str += renderExpressionWithConstantMark(s.data.get(i), useOriginalNames, code);
                    if (i != s.data.size()-1) {
                        str += ", ";
                    }
                }
                if (s.comment != null) str += "  " + s.comment;
                return str;
            }

            case CodeStatement.STATEMENT_DEFINE_SPACE:
            {
                // SDCC does not allow a "value":
                return "    "+config.lineParser.KEYWORD_DS+" " + s.space;
            }
            
            default:
                return s.toStringUsingRootPath(rootPath, useOriginalNames, true);
        }
    }    
    
    
    @Override
    public String getNextTemporaryLabel()
    {
        nextTemporaryLabel++;
        return (nextTemporaryLabel-1) + "$";
    }
}

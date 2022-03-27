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
import code.HTMLCodeStyle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parser.SourceLine;

/**
 *
 * @author santi
 */
public class PasmoDialect implements Dialect {
    private final String procPrefix = "__pasmo_proc__";
    
    private final MDLConfig config;
    
    int procCounter = 0;
    List<String> localLabels = new ArrayList<>();
    List<Integer> localLabelsTrail = new ArrayList<>();
    
    String currentScope = "";
    List<String> currentScopeStack = new ArrayList<>();
    
    List<CodeStatement> auxiliaryStatementsToRemoveIfGeneratingDialectasm = new ArrayList<>();
    
    public PasmoDialect(MDLConfig a_config) {
        super();

        config = a_config;
        
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);

        config.warning_jpHlWithParenthesis = false;  // I don't think Pasmo supports "jp hl"
        config.fix_tniasm_parenthesisExpressionBug = true;  // Pasmo has the same bug as tniasm

        config.tokenizer.allowAndpersandHex = true;
        
        config.expressionParser.OP_BIT_XOR = "xor";
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "high", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "low", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "defined", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);        
        config.expressionParser.dialectFunctionsOptionalSingleArgumentNoParenthesis.add("nul");
        
        // PASMO has a different operator precedence than the standard C/C++:
        config.expressionParser.OPERATOR_PRECEDENCE = new int[] {
            -1, -1, -1, -1, 6,
            -1, 4, 4, 3, 3,     // (, +, -, *, /
            3, 8, 7, 5, 5,   // %, ||, &&, =, <
            5, 5, 5, 5, 10,    // >, <=, >=, !=, ?
            3, 3, 7, 7, 6,    // <<, >>, |, &, ~
            6, 6, -1, -1, -1}; // ^, !            
        
        config.lineParser.keywordsHintingANonScopedLabel.add("defl");       
        config.lineParser.emptyStringDefaultArgumentsForMacros = true;
        
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code)
    {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("public")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("local")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".error")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".warning")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("defl") && 
            label != null) {
            // only if there is a single expression afterwwards:
            List<String> tmpTokens = new ArrayList<>();
            for(int i = 1;i<tokens.size();i++) {
                tmpTokens.add(tokens.get(i));
            }
            Expression tmp = config.expressionParser.parse(tmpTokens, null, null, code);
            if (tmp == null) return false;
            if (!tmpTokens.isEmpty() && !config.tokenizer.isSingleLineComment(tmpTokens.get(0))) {
                return false;
            }
            return true;
        }
        
        return false;
    }
    

    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement previous) 
    {
        if (name.equalsIgnoreCase("proc") ||
            name.equalsIgnoreCase("endp") ||
            name.equalsIgnoreCase("public") ||
            name.equalsIgnoreCase("local") ||
            name.equalsIgnoreCase("end") ||
            name.equalsIgnoreCase(".error") ||
            name.equalsIgnoreCase(".warning")) {
            return null;
        }
        if (localLabels.contains(name)) {
            return Pair.of(currentScope + name, null);
        } else {
            return Pair.of(config.lineParser.getLabelPrefix() + name, null);
        }
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement previous) 
    {
        if (localLabels.contains(name)) {
            return Pair.of(currentScope + name, null);
        } else {
            return Pair.of(name, null);
        }
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) {
            tokens.remove(0);
            String label = procPrefix + procCounter;
            procCounter++;
            currentScopeStack.add(0, currentScope);
            currentScope = label + ".";
            localLabelsTrail.add(localLabels.size());
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) {
            tokens.remove(0);
            currentScope = currentScopeStack.remove(0);
            int len = localLabelsTrail.remove(localLabelsTrail.size()-1);
            while(localLabels.size()>len) localLabels.remove(localLabels.size()-1);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("public")) {
            // ignore
            return true;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("local")) {
            tokens.remove(0);
            
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                localLabels.add(tokens.remove(0));
                if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) break;
                if (!tokens.remove(0).equals(",")) {
                    config.error("Cannot parse label list in " + sl);
                }
            }
            
            return true;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("end")) {
            // ignore
            return true;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".error")) {
            config.error(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".warning")) {
            config.warn(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && (tokens.get(0).equalsIgnoreCase("set") || tokens.get(0).equalsIgnoreCase("defl")) && 
            s.label != null) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode in " + sl);
                return false;
            }
            
            s.label.clearCache();
            s.label.resolveEagerly = true;            
            
            tokens.remove(0);
            if (!config.lineParser.parseEqu(tokens, l, sl, s, previous, source, code)) return false;
            Integer value = s.label.exp.evaluateToInteger(s, code, false, previous);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return false;
            }
            Expression exp = Expression.constantExpression(value, config);
            s.label.exp = exp;
            s.label.clearCache();
            
            // these variables should not be part of the source code:
            l.clear();
            return true;
        }
        
        return false;
    }    
    
    
    @Override
    public Integer evaluateExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code, boolean silent)
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
        if (functionName.equalsIgnoreCase("defined") && args.size() == 1) {
            Expression arg = args.get(0);
            if (arg.type == Expression.EXPRESSION_SYMBOL) {
                if (code.getSymbol(arg.symbolName) == null) {
                    return Expression.FALSE;
                } else {
                    return Expression.TRUE;
                }
            }
        }
        if (functionName.equalsIgnoreCase("nul")) {
            if (args.isEmpty()) {
                return Expression.TRUE;
            } else {
                return Expression.FALSE;
            }
        }
        return null;
    }
    
    
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code) {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            Expression exp = Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, Expression.RENDER_AS_16BITHEX, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
//            exp.originalDialectExpression = ???;
            return exp;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, Expression.RENDER_AS_16BITHEX, config), config);
        }
        return null;
    }
    
    
    @Override
    public boolean postParseActions(CodeBase code)
    {                  
        {
            CodeStatement firstGeneratingBytes = null;
            CodeStatement lastGeneratingBytes = null;
            CodeStatement s = code.outputs.get(0).main.getNextStatementTo(null, code);
            while(s != null) {
                if (s.type == CodeStatement.STATEMENT_INCLUDE && !s.include.getStatements().isEmpty()) {
                    s = s.include.getStatements().get(0);
                }
                if (s.sizeInBytes(code, false, true, false) > 0) {
                    if (firstGeneratingBytes == null) firstGeneratingBytes = s;
                    lastGeneratingBytes = s;
                }
                s = s.source.getNextStatementTo(s, code);
            }

            // "org/page" in asMSX work different than in some other assemblers, and we need to fill with zeros 
            // the space until reaching the address of the org/page! (also, if you have two orgs with the same
            // address, the latter code will overwrite the first:
            if (firstGeneratingBytes != null && lastGeneratingBytes != null) {
                CodeStatement previous = null;
                s = firstGeneratingBytes;
                while(s != lastGeneratingBytes) {
                    if (s.type == CodeStatement.STATEMENT_INCLUDE && !s.include.getStatements().isEmpty()) {
                        s = s.include.getStatements().get(0);
                    }
                    if (previous != null && s.type == CodeStatement.STATEMENT_ORG) {
                        int previousAddress = previous.getAddress(code) + previous.sizeInBytes(code, false, true, false);
                        int orgAddress = s.org.evaluateToInteger(s, code, false);
                        int pad = 0;
                        Expression padExp = null;
                        if (orgAddress > previousAddress) {
                            pad = orgAddress - previousAddress;
                            padExp = Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                                    Expression.parenthesisExpressionIfNotConstant(s.org, "(",config), 
                                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), config);
                        }

                        if (pad > 0 && padExp != null) {
                            // we need to insert filler space:
                            config.debug("pasmo: pad: " + pad + " to reach " + orgAddress);
                            CodeStatement padStatement = new CodeStatement(CodeStatement.STATEMENT_DEFINE_SPACE, null, previous.source, config);
                            padStatement.space = padExp;
                            padStatement.space_value = Expression.constantExpression(0, config);
                            previous.source.addStatement(previous.source.getStatements().indexOf(previous)+1, padStatement);                            
                            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(padStatement);
                        }
                    }
                    previous = s;
                    s = s.source.getNextStatementTo(s, code);                    
                }
            }
        }
                
        return true;
    }    
    
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code, Path rootPath, HTMLCodeStyle style) {
        boolean useOriginalNames = true;
        if (auxiliaryStatementsToRemoveIfGeneratingDialectasm.contains(s)) return "";
        
        return s.toStringUsingRootPath(rootPath, useOriginalNames, true, code, style);
    }      
}

/*
 * Author: Santiago Ontañón Villar (Brain Games)
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
public class WinAPEDialect implements Dialect {

    private final MDLConfig config;
    
    public String writingTo = null;

    // Some lines do not make sense in standard zilog assembler, and MDL removes them,
    // but if we want to generate assembler targetting this dialect, we need those lines.
    List<SourceLine> linesToKeepIfGeneratingDialectAsm = new ArrayList<>(); 
    List<CodeStatement> auxiliaryStatementsToRemoveIfGeneratingDialectasm = new ArrayList<>();
    
    
    public WinAPEDialect(MDLConfig a_config) {
        super();

        config = a_config;
        
        config.lineParser.KEYWORD_INCLUDE = "read";
        
        config.warning_jpHlWithParenthesis = false;  // I don't think WinAPE supports "jp hl"
        config.tokenizer.allowAndpersandHex = true;
        config.caseSensitiveSymbols = false;
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("write")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("close")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("align")) return true;
        
        return false;
    }
    

    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement previous) 
    {
        if (name.equalsIgnoreCase("write") ||
            name.equalsIgnoreCase("close") ||
            name.equalsIgnoreCase("align")) {
            return null;
        }
        return Pair.of(config.lineParser.getLabelPrefix() + name, null);
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement previous) 
    {
        return Pair.of(name, null);
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("write")) {
            tokens.remove(0);
            
            if (writingTo != null) {
                config.warn("More than one binary file in a single assembler file not yet supported for the WinAPE dialect. If you need this feature, please open an issue in GitHub.");
            }
            
            writingTo = tokens.remove(0);
            
            code.outputs.get(0).fileName = writingTo;
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("close")) {
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
            // ds (((($-1)/exp)+1)*exp-$)
            s.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                        Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                          Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                              Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                                Expression.parenthesisExpression(
                                  Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                                      Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), 
                                      Expression.constantExpression(1, config), config), "(", config),
                                  exp, config), 
                              Expression.constantExpression(1, config), config), "(", config), 
                          exp, config),
                        Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), config);
            s.space_value = Expression.constantExpression(0, config);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }           
        
        return false;
    }    
    
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code , Path rootPath, HTMLCodeStyle style) {
        boolean useOriginalNames = true;
        if (linesToKeepIfGeneratingDialectAsm.contains(s.sl)) {
            return s.sl.line;
        }

        if (auxiliaryStatementsToRemoveIfGeneratingDialectasm.contains(s)) return "";
        
        return s.toStringUsingRootPath(rootPath, useOriginalNames, true, code, style);
    }    
    
}

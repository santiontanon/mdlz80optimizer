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
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parser.SourceLine;

/**
 * tniASM 0.45 Dialect
 * @author theNestruo
 */
public class TniAsmDialect implements Dialect {

    private final MDLConfig config;


    /**
     * Constructor
     * @param a_config
     */
    public TniAsmDialect(MDLConfig a_config) {
        super();

        config = a_config;
        
        if (!config.hexStyleChanged) {
            config.hexStyle = MDLConfig.HEX_STYLE_0X;
        }
        
        config.warning_jpHlWithParenthesis = false;  // I don't think tniasm supports "jp hl"
        config.fix_tniasm_parenthesisExpressionBug = true;

        config.preProcessor.macroSynonyms.put("ifexist", config.preProcessor.MACRO_IFDEF);

        config.lineParser.addKeywordSynonym("rb", config.lineParser.KEYWORD_DS);
        
        config.lineParser.defineSpaceVirtualByDefault = true;
        config.caseSensitiveSymbols = false;
        
        config.expressionParser.OP_BIT_NEGATION = "not";
        config.expressionParser.OP_BIT_OR = "or";
        config.expressionParser.OP_BIT_AND = "and";
        config.expressionParser.OP_BIT_XOR = "xor";
        config.expressionParser.OP_MOD = "mod";
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("fname")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("forg")) return true;
        return false;
    }
    
    
    private SourceConstant getLastAbsoluteLabel(CodeStatement s) 
    {
        // sjasm considers any label as an absolute label, even if it's associated with an equ,
        // so, no need to check if s.label.isLabel() (as in asMSX):
        while(s != null) {
            if (s.label != null && !s.label.originalName.startsWith(".")) {
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
        if (name.startsWith(".")) {
            SourceConstant lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.originalName + name, lastAbsoluteLabel);
            }
        }

        // An absolute label
        return Pair.of(config.lineParser.getLabelPrefix() + name, null);
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement s) {
        // A relative label
        if (name.startsWith(".")) {
            SourceConstant lastAbsoluteLabel = getLastAbsoluteLabel(s);
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.originalName + name, lastAbsoluteLabel);
            }
        }

        // An absolute label
        return Pair.of(name, null);
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l,
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) {
            tokens.remove(0);
            
            // Parse it as a "ds", but multiply the number by 2:
            if (!config.lineParser.parseDefineSpace(tokens, l, sl, s, previous, source, code)) return false;
            if (s.space != null) {
                s.space = Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                        s.space, 
                        Expression.constantExpression(2, config), config);
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }   
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("fname")) {
            tokens.remove(0);
            tokens.remove(0);   // file name
            // just ignore
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("forg")) {
            tokens.remove(0);
            tokens.remove(0);   // file name
            // just ignore for now
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        
        return false;
    }
}

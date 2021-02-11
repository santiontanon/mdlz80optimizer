/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;


import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.List;
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
        
        config.warning_jpHlWithParenthesis = false;  // I don't think tniasm supports "jp hl"

        config.preProcessor.macroSynonyms.put("ifexist", config.preProcessor.MACRO_IFDEF);

        config.lineParser.addKeywordSynonym("rb", config.lineParser.KEYWORD_DS);
        
        config.lineParser.defineSpaceVirtualByDefault = true;
        config.lineParser.caseSensitiveSymbols = false;
        config.expressionParser.caseSensitiveSymbols = false;
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("fname")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("forg")) return true;
        return false;
    }
    
    
    private String getLastAbsoluteLabel(SourceStatement s) 
    {
        // sjasm considers any label as an absolute label, even if it's associated with an equ,
        // so, no need to check if s.label.isLabel() (as in asMSX):
        while(s != null) {
            if (s.label != null && !s.label.originalName.startsWith(".")) {
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
        if (name.startsWith(".")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name;
            }
        }

        // An absolute label
        return config.lineParser.getLabelPrefix() + name;
    }


    @Override
    public String symbolName(String name, SourceStatement s) {
        // A relative label
        if (name.startsWith(".")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(s);
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name;
            }
        }

        // An absolute label
        return name;
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<SourceStatement> l,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code)
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

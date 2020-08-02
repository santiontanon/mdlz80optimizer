/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;


import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
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
        
        config.warningJpHlWithParenthesis = false;  // I don't think tniasm supports "jp hl"

        config.preProcessor.macroSynonyms.put("ifexist", config.preProcessor.MACRO_IFDEF);

        config.lineParser.addKeywordSynonym("rb", config.lineParser.KEYWORD_DS);
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) return true;
        return false;
    }
    
    
    private String getLastAbsoluteLabel(SourceStatement s) 
    {
        while(s != null) {
            if (s.label != null && s.label.isLabel() && !s.label.originalName.startsWith(".")) {
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
        return name;
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
    public List<SourceStatement> parseLine(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code)
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) {
            tokens.remove(0);
            
            // Parse it as a "ds", but multiply the number by 2:
            if (!config.lineParser.parseDefineSpace(tokens, sl, s, previous, source, code)) return null;
            if (s.space != null) {
                s.space = Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                        s.space, 
                        Expression.constantExpression(2, config), config);
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
        }   
        
        return null;
    }
}

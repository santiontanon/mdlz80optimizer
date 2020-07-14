/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;


import org.apache.commons.lang3.StringUtils;

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

    private String lastAbsoluteLabel;

    /**
     * Constructor
     * @param a_config
     */
    public TniAsmDialect(MDLConfig a_config) {
        super();

        config = a_config;
        
        config.warningJpHlWithParenthesis = false;  // I don't think tniasm supports "jp hl"

        lastAbsoluteLabel = null;

        config.preProcessor.macroSynonyms.put("ifexist", config.preProcessor.MACRO_IFDEF);

        config.lineParser.addKeywordSynonym("rb", config.lineParser.KEYWORD_DS);
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) return true;
        return false;
    }
    

    @Override
    public String newSymbolName(String name, Expression value) {

        // A relative label
        if (StringUtils.startsWith(name, ".")) {
            return lastAbsoluteLabel + name;
        }

        // When a name has "CURRENT_ADDRESS" as its value, it means it's a label.
        // If it does not start by ".", then it's an absolute label:
        if ((value != null)
                && (value.type == Expression.EXPRESSION_SYMBOL)
                && value.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
            lastAbsoluteLabel = name;
        }

        // An absolute label
        return name;
    }


    @Override
    public String symbolName(String name) {

        return StringUtils.startsWith(name, ".")
                ? lastAbsoluteLabel + name
                : name;
    }
    
    
    @Override
    public List<SourceStatement> parseLine(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("rw")) {
            tokens.remove(0);
            
            // Parse it as a "ds", but multiply the number by 2:
            if (!config.lineParser.parseDefineSpace(tokens, sl, s, source, code)) return null;
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

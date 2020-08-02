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
import parser.SourceLine;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class WinAPEDialect implements Dialect {

    private final MDLConfig config;
    
    public String writingTo = null;

    public WinAPEDialect(MDLConfig a_config) {
        super();

        config = a_config;
        
        config.lineParser.KEYWORD_INCLUDE = "read";
        
        config.warningJpHlWithParenthesis = false;  // I don't think WinAPE supports "jp hl"
        Tokenizer.allowAndpersandHex = true;
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("write")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("close")) return true;
        
        return false;
    }
    

    @Override
    public String newSymbolName(String name, Expression value, SourceStatement previous) 
    {
        if (name.equalsIgnoreCase("write") ||
            name.equalsIgnoreCase("close")) {
            return null;
        }
        return name;
    }


    @Override
    public String symbolName(String name, SourceStatement previous) 
    {
        return name;
    }
    
    
    @Override
    public List<SourceStatement> parseLine(List<String> tokens, SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code)
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("write")) {
            tokens.remove(0);
            
            if (writingTo != null) {
                config.warn("MDL does not support generating more than one binary file in a single assembler file. If you are generating assembler code directly with MDL, it will not result in a correct binary.");
            }
            
            writingTo = tokens.remove(0);
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("close")) {
            tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        
        return null;
    }    
}

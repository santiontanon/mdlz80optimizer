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
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parser.SourceLine;
import parser.Tokenizer;

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
    
    public PasmoDialect(MDLConfig a_config) {
        super();

        config = a_config;
                
        config.warning_jpHlWithParenthesis = false;  // I don't think WinAPE supports "jp hl"
    }

    
    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("public")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("local")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        
        return false;
    }
    

    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, SourceStatement previous) 
    {
        if (name.equalsIgnoreCase("proc") ||
            name.equalsIgnoreCase("endp") ||
            name.equalsIgnoreCase("public") ||
            name.equalsIgnoreCase("local") ||
            name.equalsIgnoreCase("end")) {
            return null;
        }
        if (localLabels.contains(name)) {
            return Pair.of(currentScope + name, null);
        } else {
            return Pair.of(config.lineParser.getLabelPrefix() + name, null);
        }
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, SourceStatement previous) 
    {
        if (localLabels.contains(name)) {
            return Pair.of(currentScope + name, null);
        } else {
            return Pair.of(name, null);
        }
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<SourceStatement> l, SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code)
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
            
            while(!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
                localLabels.add(tokens.remove(0));
                if (tokens.isEmpty() || Tokenizer.isSingleLineComment(tokens.get(0))) break;
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
        
        return false;
    }    
}

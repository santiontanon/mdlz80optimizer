/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import util.Pair;

public class CodeBaseParser {
    MDLConfig config;
  
    
    public CodeBaseParser(MDLConfig a_config) 
    {
        config = a_config;
    }
    
    
    public boolean isMacro(String name, CodeBase code)
    {
        if (config.preProcessor == null) return false;
        return config.preProcessor.isMacro(name, code);
    }    
     

    public SourceFile parseMainSourceFile(String fileName, CodeBase code) throws Exception
    {
        SourceFile main = parseSourceFile(fileName, code, null, null);
        if (main == null) return null;
        
        // Expanc all macros that were not expanded initially:
        for(SourceFile f:code.getSourceFiles()) {
            if (!expandAllMacros(f, code)) {
                config.error("Problem expanding macros after loading all the source code!");
                return null;
            }
        }
        
        return main;
    }

    
    public SourceFile parseSourceFile(String fileName, CodeBase code, SourceFile parent, SourceStatement parentInclude) 
    {
        if (code.getSourceFile(fileName) != null) {
            config.warn("Re-entering into " + fileName + " ignored...");
            return null;
        }

        SourceFile f = new SourceFile(fileName, parent, parentInclude, config);
        if (parent == null) code.setMain(f);
        code.addSourceFile(f);
        try {
            if (parseSourceFileInternal(f, code, config)) return f;
        } catch(Exception e) {
            config.error("Problem parsing file " + fileName);
            e.printStackTrace();
        }
        return null;
    }
    
    
    Pair<String, Integer> getNextLine(BufferedReader br, int lineNumber, List<String> tokens) throws Exception
    {
        String line = config.preProcessor.expandMacros();
        if (line == null) {
            if (br != null) line = br.readLine();
            if (line == null) return null;
            lineNumber++;                
        }
        
        Tokenizer.tokenize(line, tokens);
        if (!tokens.isEmpty() && tokens.get(tokens.size()-1).equals(",")) {
            // unfinished line, get the next one!
            List<String> tokens2 = new ArrayList<>();
            Pair<String, Integer> tmp = getNextLine(br, lineNumber, tokens2);
            if (tmp != null) {
                line += "\n" + tmp.m_a;
                tokens.addAll(tokens2);
                lineNumber = tmp.m_b;
            }
        }
        
        return new Pair<>(line, lineNumber);
    }
    
    
    boolean parseSourceFileInternal(SourceFile f, CodeBase code, MDLConfig config) throws Exception
    {
        config.debug("Parsing " + f.fileName + "...");
        
        BufferedReader br = new BufferedReader(new FileReader(f.fileName));
        int lineNumber = 0;
        while(true) {
            List<String> tokens = new ArrayList<>();
            Pair<String, Integer> tmp = getNextLine(br, lineNumber, tokens);
            if (tmp == null) {
                if (config.preProcessor.withinMacroDefinition()) {
                    config.error("File " +f.fileName+ " ended while inside a macro definition");
                    return false;
                }
                return true;
            }
            String line = tmp.m_a;
            lineNumber = tmp.m_b;
            
            if (config.preProcessor.withinMacroDefinition()) {
                if (!config.preProcessor.parseMacroLine(tokens, line, lineNumber, f, code, config)) return false;
            } else {
                SourceStatement s = config.lineParser.parse(tokens, line, lineNumber, f, code, config);
                if (s == null) return false;
                if (!s.isEmpty()) {
                    if (!config.preProcessor.handleStatement(line, lineNumber, s, f, code)) {
                        f.addStatement(s);
                    }
                }
            }
        }
    }
    
    
    public boolean expandAllMacros(SourceFile f, CodeBase code) throws Exception
    {
        for(int i = 0;i<f.getStatements().size();i++) {
            SourceStatement s_macro = f.getStatements().get(i);
            if (s_macro.type == SourceStatement.STATEMENT_MACROCALL) {
                // expand macro!
                config.info("Expanding macro: " + s_macro.macroCallName);
                
                if (!config.preProcessor.handleStatement("", s_macro.lineNumber, s_macro, f, code)) {
                    config.error("Cannot expand macro " + s_macro.macroCallName);
                    return false;
                }
                
                f.getStatements().remove(i);
                
                // Parse the new lines (which could, potentially trigger other macros!):
                while(true) {
                    List<String> tokens = new ArrayList<>();
                    Pair<String, Integer> tmp = getNextLine(null, s_macro.lineNumber, tokens);
                    if (tmp == null) {
                        if (config.preProcessor.withinMacroDefinition()) {
                            config.error("File " + f.fileName + " ended while inside a macro definition");
                            return false;
                        }
                        break;
                    }
                    String line = tmp.m_a;
                    if (config.preProcessor.withinMacroDefinition()) {
                        if (!config.preProcessor.parseMacroLine(tokens, line, s_macro.lineNumber, f, code, config)) return false;
                    } else {
                        SourceStatement s = config.lineParser.parse(tokens, line, s_macro.lineNumber, f, code, config);
                        if (s == null) return false;
                        if (!s.isEmpty()) {
                            if (!config.preProcessor.handleStatement(line, s_macro.lineNumber, s, f, code)) {
                                f.addStatement(i, s);
                            }
                        }
                    }
                }
                i--;
            }
        }
        return true;
    }
    
   
}

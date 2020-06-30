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
     
    
    public SourceFile parseSourceFile(String fileName, CodeBase code, SourceFile parent, SourceStatement parentInclude) 
    {
        if (code.getSourceFile(fileName) != null) {
            config.warn("Re-entering into " + fileName + " ignored...");
            return null;
        }

        SourceFile f = new SourceFile(fileName, parent, parentInclude);
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
    
    
    boolean parseSourceFileInternal(SourceFile f, CodeBase code, MDLConfig config) throws Exception
    {
        config.debug("Parsing " + f.fileName + "...");
        
        BufferedReader br = new BufferedReader(new FileReader(f.fileName));
        int lineNumber = 0;
        while(true) {
            String line = config.preProcessor.expandMacros();
            if (line == null) {
                line = br.readLine();
                lineNumber++;                
            }
            if (line == null) {
                if (config.preProcessor.withinMacroDefinition()) {
                    config.error("File " +f.fileName+ " ended while inside a macro definition");
                    return false;
                }
                return true;
            }
            
            if (config.preProcessor.withinMacroDefinition()) {
                if (!config.preProcessor.parseMacroLine(line, lineNumber, f, code, config)) return false;
            } else {
                SourceStatement s = config.lineParser.parse(line, lineNumber, f, code, config);
                if (s == null) return false;
                if (!s.isEmpty()) {
                    if (!config.preProcessor.handleStatement(line, lineNumber, s, f, code)) {
                        f.addStatement(s);
                    }
                }
            }
        }
    }
    
   
}

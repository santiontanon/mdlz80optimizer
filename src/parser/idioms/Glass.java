/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.idioms;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class Glass implements Idiom {
    MDLConfig config;
    List<String> sectionStack = new ArrayList<>();
    
    public Glass(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        return false;
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) {
            // TODO(santi@): implement "section" with the same semantics as Glass. I am currently just
            // approximating it by replacing it with "org"
            tokens.remove(0);
            
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;                
            }
            if (exp.type != Expression.EXPRESSION_SYMBOL) {
                config.error("Invalid section name at " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;                
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            sectionStack.add(0, exp.symbolName);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) {
            if (sectionStack.isEmpty()) {
                sectionStack.remove(0);
                return true;
            } else {
                config.error("No section to terminate at " + source.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;                
            }
        }
        return false;
    }
    
}

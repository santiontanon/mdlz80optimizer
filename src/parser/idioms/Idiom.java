/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.idioms;

import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import java.util.List;

/**
 *
 * @author santi
 */
public interface Idiom {
    public boolean recognizeIdiom(List<String> tokens);
    
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception;
}

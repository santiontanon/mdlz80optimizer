/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import java.util.List;
import parser.SourceMacro;

/**
 *
 * @author santi
 */
public interface Dialect {
    // Returns true if the line represented by "tokens" is recognized by this idiom parser
    public boolean recognizeIdiom(List<String> tokens);
    
    // If the previous function returns true, instead of trying to parse the line with the 
    // default parser, this function will be invoked instead. Returns true if it could 
    // successfully parse the line
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception;
    
    // Some idioms might do special things when macros are defined. For example,
    // Glass actually compiles the code inside macros, rather than treating it simply as 
    // text to be copy/pasted when the macro is expanded (as the default parser of MDL does).
    public boolean newMacro(SourceMacro macro, CodeBase code) throws Exception;
}

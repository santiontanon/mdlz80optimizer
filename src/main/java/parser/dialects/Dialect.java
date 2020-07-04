/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.List;

import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import parser.SourceMacro;

/**
 *
 * @author santi
 */
public interface Dialect {
    // Returns true if the line represented by "tokens" is recognized by this dialect parser
    public boolean recognizeIdiom(List<String> tokens);

    // Called when a new symbol is defined (so that the dialect parser can do whatever special it
    // needs to do with it, e.g. define local labels, etc.)
    // Returns false if we are trying to redefine a pre-defined symbol according to this dialect
    public String newSymbolName(String name, Expression value);

    // Like the previous function, but called just when a symbol is used, not when it is defined
    // Should return the actual symbol name (e.g., just "name" if this is an absolute symbol,
    // or some concatenation with a prefix if it's a relative symbol)
    public String symbolName(String name);

    // If the previous function returns true, instead of trying to parse the line with the
    // default parser, this function will be invoked instead. Returns true if it could
    // successfully parse the line
    // - returns "false" if an error occurred
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code);

    // Some dialects might do special things when macros are defined. For example,
    // Glass actually compiles the code inside macros, rather than treating it simply as
    // text to be copy/pasted when the macro is expanded (as the default parser of MDL does).
    // - returns "false" if an error occurred
    public boolean newMacro(SourceMacro macro, CodeBase code);
}

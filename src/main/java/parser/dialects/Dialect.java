/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.List;

import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;

/**
 *
 * @author santi
 */
public interface Dialect {


    // @return true if the line represented by "tokens" is recognized by this dialect parser
    default boolean recognizeIdiom(List<String> tokens) {
        // (no-op by default)
        return false;
    }

    // Called when a new symbol is defined (so that the dialect parser can do whatever special it
    // needs to do with it, e.g. define local labels, etc.)
    // returns false if we are trying to redefine a pre-defined symbol according to this dialect
    String newSymbolName(String name, Expression value);

    // Like {@link #newSymbolName the previous function}, but called just when a symbol is used, not when it is defined
    // Should return the actual symbol name (e.g., just "name" if this is an absolute symbol,
    // or some concatenation with a prefix if it's a relative symbol)
    String symbolName(String name);

    // If the previous function returns true, instead of trying to parse the line with the
    // default parser, this function will be invoked instead. Returns true if it could
    // successfully parse the line
    // @return {@code null} if an error occurred;
    // a list of statements to add as a result of parsing the line otherwise
    default List<SourceStatement> parseLine(List<String> tokens,
            SourceLine sl, SourceStatement s, SourceFile source, CodeBase code) {
        // (no-op by default)
        return null;
    }

    
    // Some dialects implement custom functions (e.g., asMSX has a "random" function). They cannot
    // be included in the general parser, as if someone uses a different assembler, those could be used
    // as macro names, causing a collision. So, they are implemented via this function:
    default Number evaluateExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code, boolean silent) {
        // (no-op by default)
        return null;
    }

    // Some dialect functions can be translated to standard expressions. This is preferable than direct evaluation, 
    // if possible, since expressions that contain labels might change value during optimization:
    default Expression translateToStandardExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code) {
        // (no-op by default)
        return null;
    }
    
    // Returns true if a function returns an integer
    default boolean expressionEvaluatesToIntegerConstant(String functionName) {
        // (no-op by default)
        return true;
    }
    
    // Called to expand any dialect-specific macros:
    default MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, SourceStatement macroCall, CodeBase code) {
        // (no-op by default)
        return null;
    }

    // Called right before the code is going to be parsed (this can be used, for example,
    // to predefine constants in the CodeBase class, like "pi" in asMSX):
    default void performAnyInitialActions(CodeBase code) {
        // (no-op by default)
    }
    
    // Called after all the code is parsed before all macros are expanded
    default boolean performAnyPostParsingActions(CodeBase code) {
        // (no-op by default)
        return true;        
    }

    // Called after all the code is parsed and all macros expanded
    default boolean performAnyFinalActions(CodeBase code) {
        // (no-op by default)
        return true;
    }
    
}

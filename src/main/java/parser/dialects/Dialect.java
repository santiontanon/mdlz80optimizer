/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.List;

import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import code.OutputBinary;
import java.nio.file.Path;
import org.apache.commons.lang3.tuple.Pair;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;
import workers.pattopt.PatternMatch;
import workers.reorgopt.CodeBlock;

/**
 *
 * @author santi
 */
public interface Dialect {
    
   
    // @return true if the line represented by "tokens" is recognized by this dialect parser.
    default boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code) {
        // (no-op by default)
        return false;
    }

    
    // Called when a new symbol is defined (so that the dialect parser can do whatever special it
    // needs to do with it, e.g. define local labels, etc.)
    // returns null if we are trying to redefine a pre-defined symbol according to this dialect.
    // It returns a pair <a,b>, where a is the name of the new symbol, and b is an absolute label, if this was local label that refers to it.
    // - "s" here is used to determine the label context. So, it should be a statement that is already in a
    //   SourceFile. If parsing a new statement that is not yet in the SourceFile, here, pass the previous statement (after
    //   which "s" will be inserted).
    Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement s);

    
    // Like the previous function, but called just when a symbol is used, not when it is defined
    // Should return the actual symbol name (e.g., just "name" if this is an absolute symbol,
    // or some concatenation with a prefix if it's a relative symbol)
    // - "s" here is used to determine the label context. So, it should be a statement that is already in a
    //   SourceFile. If parsing a new statement that is not yet in the SourceFile, here, pass the previous statement (after
    //   which "s" will be inserted).
    Pair<String, SourceConstant> symbolName(String name, CodeStatement s);

    
    // When the default line parser cannot parse a line, this function will be invoked instead. Returns true if it could
    // successfully parse the line, and false if an error occurred.
    default boolean parseLine(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl, CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
        // (no-op by default)
        return false;
    }

    
    // Some dialects allow "fake" instructions (like "ld de,hl", which do not really, exist, but expand to sequence of ops)
    // This is dangerous for several reasons, as they hide different instructions and it might not be obvious how flags are
    // affected, or what is happening under the hood, making debugging harder. So, MDL generates a warning message 
    // when these are found.
    default boolean parseFakeCPUOps(List<String> tokens, SourceLine sl, List<CodeStatement> l, CodeStatement previous, SourceFile source, CodeBase code) {
        // (no-op by default)
        return true;
    }
    
    
    // Some dialects implement custom functions (e.g., asMSX has a "random" function). They cannot
    // be included in the general parser, as if someone uses a different assembler, those could be used
    // as macro names, causing a collision. So, they are implemented via this function:
    default Number evaluateExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code, boolean silent) {
        // (no-op by default)
        return null;
    }

    
    // Some dialect functions can be translated to standard expressions. This is preferable than direct evaluation, 
    // if possible, since expressions that contain labels might change value during optimization:
    default Expression translateToStandardExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code) {
        // (no-op by default)
        return null;
    }
    
    
    // Returns true if a function returns an integer
    default boolean expressionEvaluatesToIntegerConstant(String functionName) {
        // (no-op by default)
        return true;
    }

    
    // Returns true if a function returns a string
    default boolean expressionEvaluatesToStringConstant(String functionName) {
        // (no-op by default)
        return false;
    }
    
    
    // Called to expand any dialect-specific macros:
    default MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, CodeStatement macroCall, CodeBase code) {
        // (no-op by default)
        return null;
    }

    
    // Called right before the code is going to be parsed (this can be used, for example,
    // to predefine constants in the CodeBase class, like "pi" in asMSX):
    default void performAnyInitialActions(CodeBase code) {
        // (no-op by default)
    }
    
    
    // Called after all the code is parsed before all macros are expanded
    default boolean postParsePreMacroExpansionActions(CodeBase code) {
        // (no-op by default)
        return true;        
    }

    
    // Called after all the code is parsed and all macros expanded
    default boolean postParseActions(CodeBase code) {
        // (no-op by default)
        return true;
    }
    
    
    // Called after any modification to the code was done (e.g., after an
    // optimizer is run). For example, this is used by some dialects to enforce
    // a desired page size (which might have been modified by the optimizers, 
    // who are not aware of the concept of "pages").
    default boolean postCodeModificationActions(CodeBase code) {
        // (no-op by default)
        return true;
    }
        
    
    // Translates a statement to string using the syntax of the specific dialect:
    default String statementToString(CodeStatement s, CodeBase code, Path rootPath) {
        return s.toStringUsingRootPath(rootPath, true, true, code);
    }
    
    default String getNextTemporaryLabel()
    {
        return null;
    }
    
    
    // Get the top level areas (those blocks of code that are contiguous, and
    // where MDL should be free to move things around within a block without causing problems):
    default void getBlockAreas(CodeBase code, List<CodeBlock> blocks)
    {
        for(OutputBinary output: code.outputs) {
            if (output.main != null && !output.main.getStatements().isEmpty()) {
                CodeBlock top = new CodeBlock("TB0", CodeBlock.TYPE_UNKNOWN, output.main.getStatements().get(0), null, code);
                blocks.add(top);
            }
        }
    }
    
    
    // Whether the dialect supports multiple output binaries from a single assembler file:
    default boolean supportsMultipleOutputs()
    {
        return false;
    }
    
    
    // Some dialects can define labels as "exported" (even if they are not called by
    // the code MDL is analyzing, they could be called externally). This is important
    // to prevent some optimizations that might mess up these functions (for example,
    // they cannot be inlined).
    default boolean labelIsExported(SourceConstant label)
    {
        return false;
    }
    
    
    // Some dialects might mark function starts with some special notation. 
    // MDL can exploit these annotations in a few internal methods, such as
    // "function identification" in the SourceCodeTableGenerator worker.
    default boolean hasFunctionStartMark(CodeStatement s)
    {
        if (s.label != null) {
            return labelIsExported(s.label);
        }
        return false;
    }
    
    
    // Check to prevent some optimizations that might be unsafe in certain
    // dialects.
    default boolean safeOptimization(PatternMatch match)
    {
        return true;
    }
}

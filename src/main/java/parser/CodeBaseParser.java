/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.CodeStatement;
import code.SourceConstant;
import util.Resources;

public class CodeBaseParser {
    MDLConfig config;

    // Expressions that we don't want to keep expanded, but want to replace by concrete values
    // once the code is parsed. Examples of this are dialect-specific functions, which we want
    // to resolve before generating asm output to maintain maximum compatibility in the output:
    public List<Pair<Expression, CodeStatement>> expressionsToReplaceByValueAtTheEnd = new ArrayList<>();


    public CodeBaseParser(MDLConfig a_config) {
        config = a_config;
    }


    public boolean parseMainSourceFile(String fileName, CodeBase code) throws IOException {
        if (config.dialectParser != null) config.dialectParser.performAnyInitialActions(code);
        
        // parse the symbol definitions in the flags:
        for(String definition: config.symbolDefinitions) {
            List<String> tokens = config.tokenizer.tokenize(definition);
            String symbolName = tokens.remove(0);
            if (!config.lineParser.canBeLabel(symbolName)) {
                config.error("Cannot parse flag -equ " + definition + " (symbol name is not allowed)");
                return false;
            }
            if (tokens.isEmpty() || !tokens.remove(0).equals("=")) {
                config.error("Cannot parse flag -equ " + definition);
                return false;
            }
            Expression valueExp = config.expressionParser.parse(tokens, null, null, code);
            if (valueExp == null ||
                !tokens.isEmpty() ||
                !valueExp.isConstant()) {
                // assume it's a string:
                String stringValue = definition.substring(definition.indexOf("="));
                valueExp = Expression.constantExpression(stringValue, config);
            }
            Object value = valueExp.evaluate(null, code, true);
            if (value == null) {
                config.error("Cannot parse flag -equ " + definition);
                return false;
            }
            SourceConstant sc = new SourceConstant(symbolName, symbolName, valueExp, null, config);
            code.addSymbol(symbolName, sc);
        }
        
        if (parseSourceFile(fileName, code, null, null) == null) {
            return false;
        }

        // Dialect actions before expanding all macros:
        if (config.dialectParser != null) {
            if (!config.dialectParser.performAnyPostParsingActions(code)) {
                return false;
            }
        }
        
        // Resolve local labels:
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                s.resolveLocalLabels(code);
            }
        }        

        code.resetAddresses();
        
        // Expand all macros that were not expanded initially:
        if (!expandAllMacros(code)) {
            config.error("Problem expanding macros after loading all the source code!");
            return false;
        }
        
        // Replace eager variables (those that will not be defined in the 
        // assembler output if we save the result of optimization back to assembler),
        // by their values:
        resolveUnresolvedEagerSymbols(code);
        
        // Improve code safety for potential optimizations 
        // (for example, replace things like "jr $+5" with proper labels, so the code can be optimized safely):
        improveCodeSafety(code);
        
        // Find the optimization protected regions:
        findOptimizationProtectedRegions(code);
        
        if (config.dialectParser != null) {
            if (!config.dialectParser.postParseActions(code)) return false;
        }
        for(Pair<Expression, CodeStatement> pair:expressionsToReplaceByValueAtTheEnd) {
            Expression exp = pair.getLeft();
            if (config.dialectParser != null && exp.dialectFunction != null) {
                Expression translated = config.dialectParser.translateToStandardExpression(exp.dialectFunction, exp.args, pair.getRight(), code);
                if (translated != null) {
                    Expression original = exp.clone();
                    exp.copyFrom(translated);
                    exp.originalDialectExpression = original;
                    continue;
                }
            }
            
            Object value = exp.evaluate(pair.getRight(), code, false);
            if (value == null) {
                config.error("Cannot resolve expression " + exp + " after loading all the source code!");
                return false;
            }
            if (value instanceof Integer) {
                exp.type = Expression.EXPRESSION_INTEGER_CONSTANT;
                exp.integerConstant = (Integer)value;
            } else if (value instanceof Double) {
                exp.type = Expression.EXPRESSION_DOUBLE_CONSTANT;
                exp.doubleConstant = (Double)value;
            } else if (value instanceof String) {
                exp.type = Expression.EXPRESSION_STRING_CONSTANT;
                exp.stringConstant = (String)value;
            } else {
                config.error("Cannot resolve expression " + exp + " to a number of a string!");
                return false;
            }
        }

        return true;
    }
    
    public String renameDuplicatedSourceFile(String fileName, CodeBase code)
    {
        int idx = fileName.lastIndexOf(".");
        String extension = "";
        if (idx > 0) {
            extension = fileName.substring(idx);
            fileName = fileName.substring(0, idx);            
        }
        for(int i = 2;;i++) {
            String newName = fileName + "-" + i + extension;
            if (code.getSourceFile(newName) == null) return newName;
        }
    }
    
    

    public SourceFile parseSourceFile(String fileName, CodeBase code, SourceFile parent,
            CodeStatement parentInclude) {
        String codeBaseFileName = fileName;
        if (code.getSourceFile(fileName) != null) {
            codeBaseFileName = renameDuplicatedSourceFile(fileName, code);
            config.warn("Re-entering into "+fileName+" (internally renaming to " +codeBaseFileName+ ")");
        }

        SourceFile f = new SourceFile(fileName, parent, parentInclude, code, config);
        f.fileName = codeBaseFileName;
        if (parent == null) {
            code.addOutput(null, f, 0);
        }
        code.addSourceFile(f);
        config.preProcessor.pushState();
        try {
            if (parseSourceFileInternal(f, code, config)) {
                config.preProcessor.popState();
                return f;
            } else {
                config.error("Problem parsing file " + fileName);            
            }
        } catch (Exception e) {
            config.error("Problem parsing file " + fileName + ": " + e);
            for(Object st:e.getStackTrace()) {
                config.error("    " + st);
            }
        }
        config.preProcessor.popState();
        return null;
    }

    // Returns: <SourceLine, file_linenumber>
    Pair<SourceLine, Integer> getNextLine(BufferedReader br, SourceFile f, int file_linenumber, List<String> tokens)
            throws IOException
    {
        SourceLine sl = config.preProcessor.expandMacros();
        if (sl == null) {
            String line = null;
            if (br != null) line = br.readLine();
            if (line == null) return null;
            file_linenumber++;
            sl = new SourceLine(line, f, file_linenumber);
        }

        config.tokenizer.tokenize(sl.line, tokens);
        if (config.considerLinesEndingInCommaAsUnfinished &&
            !tokens.isEmpty() && tokens.get(tokens.size()-1).equals(",")) {
            // unfinished line, get the next one!
            List<String> tokens2 = new ArrayList<>();
            Pair<SourceLine, Integer> tmp = getNextLine(br, sl.source, file_linenumber, tokens2);
            if (tmp != null) {
                sl.line += "\n" + tmp.getLeft().line;
                tokens.addAll(tokens2);
                file_linenumber = tmp.getRight();
            }
        }

        // remove multi-line comments
        List<String> filteredTokens = new ArrayList<>();
        boolean withinMultilineComment = false;
        for(String token:tokens) {
            if (withinMultilineComment) {
                if (config.tokenizer.isMultiLineCommentEnd(token)) {
                    withinMultilineComment = false;
                }
            } else {
                if (config.tokenizer.isMultiLineCommentStart(token)) {
                    withinMultilineComment = true;
                } else {
                    filteredTokens.add(token);
                }
            }
        }
        // we only want to remove those comments that are within a line, 
        // if the comment is after a line and spans multiple lines, we filter it out
        // somewhere else.
        // Note: this seems overkill, but the probelm is that MDL tries to preserve 
        // the comments, rather than just filtering out as usually done in pre-processors,
        // since it needs to be able to re-create source code again, with the comments
        // as close to what they were as possible.
        if (!withinMultilineComment && tokens.size() > filteredTokens.size()) {
            tokens.clear();
            tokens.addAll(filteredTokens);
        }
        

        return Pair.of(sl, file_linenumber);
    }


    boolean parseSourceFileInternal(SourceFile f, CodeBase code, MDLConfig config) throws IOException
    {
        try (BufferedReader br = Resources.asReader(f.originalFileName)) {
            int file_lineNumber = 0;
            while(true) {
                List<String> tokens = new ArrayList<>();
                Pair<SourceLine, Integer> tmp = getNextLine(br, f, file_lineNumber, tokens);
                if (tmp == null) {
                    if (config.preProcessor.withinMacroDefinition()) {
                        SourceMacro macro = config.preProcessor.getCurrentMacro();
                        SourceLine macroLine = macro.lines.iterator().next(); // (first macro line)
                        config.error(
                                "File " + f.fileName + " ended while inside a macro definition of \"" + macro.name + "\" "
                                + "at #" + macroLine.lineNumber + ": " + macroLine.line);
                        return false;
                    }
                    return true;
                }
                SourceLine sl = tmp.getLeft();
                file_lineNumber = tmp.getRight();
                
                if (!parseSourceFileLineInternal(tokens, sl, file_lineNumber, f, code, config)) return false;
            }
        }
    }

    
    boolean parseSourceFileLineInternal(List<String> tokens, SourceLine sl, int file_lineNumber, SourceFile f, CodeBase code, MDLConfig config) throws IOException
    {
        if (config.preProcessor.withinMacroDefinition()) {
            List<CodeStatement> newStatements =  config.preProcessor.parseMacroLine(tokens, sl, f, code, config);
            if (newStatements == null) {
                config.error("Error parsing source file " + f.fileName + " while within a macro expansion");
                return false;
            } else {
                for(CodeStatement s:newStatements) {
                    if (config.eagerMacroEvaluation ||
                        (config.macrosToEvaluateEagerly.contains(s.macroCallName))) {
                        List<CodeStatement> l2 = config.preProcessor.handleStatement(sl, s, f, code, true);
                        if (l2 == null) {
                            f.addStatement(s);
                        } else {
                            for(CodeStatement s2:l2) {
                                f.addStatement(s2);
                            }
                        }
                    } else {
                        f.addStatement(s);                            
                    }
                }
            }
        } else {
            List<CodeStatement> l = config.lineParser.parse(tokens, sl, f, f.getStatements().size(), code, config);
            if (l == null) {
                config.error("Error parsing source file " + f.fileName + " in " + sl);
                return false;
            }
            for(CodeStatement s:l) {
                int macroNestingLevel = config.preProcessor.getCurrentMacroStateNExpansions();
                List<CodeStatement> l2 = config.preProcessor.handleStatement(sl, s, f, code, config.eagerMacroEvaluation);
                if (l2 == null) {
                    f.addStatement(s);
                } else {
                    for(CodeStatement s2:l2) {
                        f.addStatement(s2);
                    }
                }
                // Expand any potential macros that we encountered:
                while(config.preProcessor.getCurrentMacroStateNExpansions() > macroNestingLevel) {
                    SourceLine sl2 = config.preProcessor.expandMacros();
                    if (sl2 != null) {
                        List<String> tokens2 = config.tokenizer.tokenize(sl2.line);
                        if (!parseSourceFileLineInternal(tokens2, sl2, file_lineNumber, f, code, config)) return false;
                    } else {
                        break;
                    }
                }
            }
        }
        return true;
    }
    

    public boolean expandAllMacros(CodeBase code) throws IOException
    {
        List<SourceFile> l = new ArrayList<>();
        l.addAll(code.getSourceFiles());

        int n_expanded;
        int n_failed;

        do {
            Pair<Integer,Integer> expanded;
            n_expanded = 0;
            n_failed = 0;
            for (SourceFile f : l) {
                do{
                    expanded = expandAllMacros(f, code);
                    if (expanded == null) return false;
                    n_expanded += expanded.getLeft();
                    n_failed += expanded.getRight();
                } while(expanded.getLeft() != 0);
            }
            config.debug("expandAllMacros: " + n_expanded + " / " + (n_expanded+n_failed));
            if (n_expanded == 0 && n_failed > 0) {
                config.debug("Failed to expand all macros after loading source code: " + n_failed+ " did not expand.");
                return false;
            }
        } while (n_failed > 0);

        if (code.getSourceFiles().size() > l.size()) {
            // there are more files, we need to expand macros again!
            return expandAllMacros(code);
        }
        return true;
    }


    public Pair<Integer,Integer> expandAllMacros(SourceFile f, CodeBase code) throws IOException
    {
        int n_expanded = 0;
        int n_failed = 0;
        for(int i = 0;i<f.getStatements().size();i++) {
            CodeStatement s_macro = f.getStatements().get(i);

            if (s_macro.type == CodeStatement.STATEMENT_MACROCALL) {
                // expand macro!
                // config.trace("expandAllMacros: Expanding macro: " + s_macro.macroCallName != null ? s_macro.macroCallName : s_macro.macroCallMacro.name);

                List<CodeStatement> l2 = config.preProcessor.handleStatement(s_macro.sl, s_macro, f, code, true);
                int insertionPoint = i;
                if (l2 == null) {
                    config.debug("Cannot yet expand macro "+s_macro.macroCallName+" in "+s_macro.sl);
                    n_failed++;
                    continue;
                } else {
                    n_expanded++;
                    f.getStatements().remove(i);
                    f.getStatements().addAll(i, l2);
                    insertionPoint += l2.size();
                }

                // We need to reset the addresses, as when we expand a macro, these can all change!
                code.resetAddresses();

                // Parse the new lines (which could, potentially contain other macros, to be expanded later):
                while(true) {
                    List<String> tokens = new ArrayList<>();
                    Pair<SourceLine, Integer> tmp = getNextLine(null, f, s_macro.sl.lineNumber, tokens);
                    if (tmp == null) {
                        if (config.preProcessor.withinMacroDefinition()) {
                            SourceMacro macro = config.preProcessor.getCurrentMacro();
                            SourceLine macroLine = macro.lines.iterator().next(); // (first macro line)
                            config.error(
                                    "File " + f.fileName + " ended while inside a macro definition of \"" + macro.name + "\" "
                                    + "at " + macroLine);
                            return null;
                        }
                        break;
                    }
                    SourceLine sl = tmp.getLeft();
                    if (config.preProcessor.withinMacroDefinition()) {
                        List<CodeStatement> newStatements =  config.preProcessor.parseMacroLine(tokens, sl, f, code, config);
                        if (newStatements == null) {
                            return null;
                        } else {
                            for(CodeStatement s:newStatements) {
                                f.addStatement(insertionPoint, s);
                                insertionPoint++;
                            }
                        }
                    } else {
                        List<CodeStatement> l = config.lineParser.parse(tokens, sl, f, insertionPoint, code, config);
                        if (l == null) return null;
                        for(CodeStatement s:l) {
                            // we set expandMacroCalls to "false" here, as if we are in this function, it means we are not evaluating
                            // macros eagerly:
                            List<CodeStatement> l3 = config.preProcessor.handleStatement(sl, s, f, code, false);
                            if (l3 == null) {
                                f.addStatement(insertionPoint, s);
                                insertionPoint++;
                            } else {
                                for(CodeStatement s3:l3) {
                                    f.addStatement(insertionPoint, s3);
                                    insertionPoint++;
                                }                                
                            }
                        }
                    }
                }

                i--;
            }
        }

        if (n_expanded > 0) {
            // resolve local labels again for all the new lines:
            for(CodeStatement s:f.getStatements()) {
                s.resolveLocalLabels(code);
            }
        }
 
        return Pair.of(n_expanded, n_failed);
    }


    public void findOptimizationProtectedRegions(CodeBase code)
    {
        for (SourceFile f : code.getSourceFiles()) {
            SourceLine currentStart = null;
            for (CodeStatement s: f.getStatements()) {
                if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION_START)) {
                    if (currentStart == null) {
                        currentStart = s.sl;
                    } else {
                        config.warn(config.PRAGMA_NO_OPTIMIZATION_START + " annotation in line " + currentStart.fileNameLineString() + " does not have a matching " + config.PRAGMA_NO_OPTIMIZATION_END);
                    }
                } else if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION_END)) {
                    if (currentStart == null) {
                        config.warn(config.PRAGMA_NO_OPTIMIZATION_END + " annotation in line " + s.sl.fileNameLineString() + " does not have a matching " + config.PRAGMA_NO_OPTIMIZATION_START);
                    } else {
                        // new pair found!
                        code.optimizationProtectedBlocks.add(Pair.of(currentStart, s.sl));
                        currentStart = null;
                    }
                }
            }
            if (currentStart != null) {
                config.warn(config.PRAGMA_NO_OPTIMIZATION_START + " annotation in line " + currentStart.fileNameLineString() + " does not have a matching " + config.PRAGMA_NO_OPTIMIZATION_END);
            }
        }
    }
    
    
    public void resolveUnresolvedEagerSymbols(CodeBase code)
    {
        for(SourceFile f : code.getSourceFiles()) {
            for(CodeStatement s: f.getStatements()) {
                for(Expression exp: s.getAllExpressions()) {
                    exp.resolveEagerSymbols(code);
                }
            }
        }
    }
    
    
    public boolean improveCodeSafety(CodeBase code)
    {
        int idx = 1;
        for(SourceFile f : code.getSourceFiles()) {
            for(CodeStatement s: f.getStatements()) {
                if (s.type == CodeStatement.STATEMENT_CPUOP && s.op.isJump()) {
                    Expression destination = s.op.getTargetJumpExpression();
                    if (destination.containsCurrentAddress()) {
                        if (replaceJumpExpressionWithLabelForSafety(s, idx, code)) {
                            idx ++;
                        }
                    } else if (destination.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                        // this is just a constant expression, replace with a label for safety!
                        if (replaceJumpExpressionWithLabelForSafety(s, idx, code)) {
                            idx ++;
                        }
                    }
                }
            }
        }
        return true;
    }
    
    
    public boolean replaceJumpExpressionWithLabelForSafety(CodeStatement s, int idx, CodeBase code)
    {
        String labelPrefix = "___MDL_SAFETY_LABEL_";
        config.warn("Safety: creating an auxiliary label for the jump in " + s.sl);

        Expression destination = s.op.getTargetJumpExpression();
        CodeStatement current = s;
        Integer targetAddress = destination.evaluateToInteger(s, code, true);

        Integer currentAddress = current.getAddress(code);
        Integer currentSize;
        int direction = 0;
        do {
            if (targetAddress < currentAddress) {
                if (direction <= 0) {
                    direction = -1;
                } else {
                    config.warn("Could not find the target address within the source in " + s.sl);
                    config.warn("Optimizations can potentially break this codebase.");
                    return false;
                }
                // go backwards:
                current = current.source.getPreviousStatementTo(current, code);
                if (current != null && current.type == CodeStatement.STATEMENT_INCLUDE &&
                    !current.include.getStatements().isEmpty()) {
                    current = current.include.getStatements().get(current.include.getStatements().size()-1);
                }
            } else {
                if (direction >= 0) {
                    direction = 1;
                } else {
                    config.warn("Could not find the target address within the source in " + s.sl);
                    config.warn("Optimizations can potentially break this codebase.");
                    return false;
                }
                // go forward:
                current = current.source.getNextStatementTo(current, code);
                if (current != null && current.type == CodeStatement.STATEMENT_INCLUDE &&
                    !current.include.getStatements().isEmpty()) {
                    current = current.include.getStatements().get(0);
                }
            }
            if (current == null || current.type == CodeStatement.STATEMENT_INCLUDE) {
                // we are in trouble, just abort
                config.warn("Unable to complete 'replaceJumpExpressionWithLabelForSafety' operation in " + s.sl);
                config.warn("Optimizations can potentially break this codebase.");
                return false;
            }
            currentAddress = current.getAddress(code);
            currentSize = current.sizeInBytes(code, true, true, true);
            if (targetAddress == null) {
                config.warn("Could not evaluate expression " + destination + " to integer in " + s.sl);
                config.warn("Optimizations can potentially break this codebase.");
                return false;
            }
            if (currentAddress == null || currentSize == null) {
                config.warn("Could not assess the address of instruction in " + s.sl);
                config.warn("Optimizations can potentially break this codebase.");
                return false;
            }
        } while (targetAddress < currentAddress || targetAddress >= currentAddress + currentSize);

        // Create a new label:
        if (current.label == null) {
            String labelName = labelPrefix + idx;
            current.label = new SourceConstant(labelName, labelName, 
                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, current, code, config), 
                    current, config);
            code.addSymbol(labelName, current.label);
        }
        int offset = targetAddress - currentAddress;
        Expression newDestination;
        if (offset == 0) {
            newDestination = Expression.symbolExpression(current.label.name, s, code, config);
        } else {
            newDestination = Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                    Expression.symbolExpression(current.label.name, s, code, config),
                    Expression.constantExpression(offset, config), config);
        }
        // replace the jump destination:
        s.op.args.set(s.op.spec.jumpLabelArgument(), newDestination);        
        return true;
    }
}

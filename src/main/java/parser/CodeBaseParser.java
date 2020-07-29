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
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import util.Resources;

public class CodeBaseParser {
    MDLConfig config;

    boolean withinMultilineComment = false;

    // Expressions that we don't want to keep expanded, but want to replace by concrete values
    // once the code is parsed. Examples of this are dialect-specific functions, which we want
    // to resolve before generating asm output to maintain maximum compatibility in the output:
    public List<Pair<Expression, SourceStatement>> expressionsToReplaceByValueAtTheEnd = new ArrayList<>();


    public CodeBaseParser(MDLConfig a_config) {
        config = a_config;
    }


    public boolean parseMainSourceFile(String fileName, CodeBase code) throws IOException {
        if (config.dialectParser != null) config.dialectParser.performAnyInitialActions(code);
        
        if (parseSourceFile(fileName, code, null, null) == null) return false;

        // Dialect actions before expanding all macros:
        if (config.dialectParser != null) {
            if (!config.dialectParser.performAnyPostParsingActions(code)) return false;
        }
        
        // Expand all macros that were not expanded initially:
        if (!expandAllMacros(code)) {
            config.error("Problem expanding macros after loading all the source code!");
            return false;
        }
        
        // Resolve local labels:
        for(SourceFile f:code.getSourceFiles()) {
            for(SourceStatement s:f.getStatements()) {
                s.resolveLocalLabels(code);
            }
        }        

        if (config.dialectParser != null) {
            if (!config.dialectParser.performAnyFinalActions(code)) return false;
        }
        for(Pair<Expression, SourceStatement> pair:expressionsToReplaceByValueAtTheEnd) {
            Expression exp = pair.getLeft();
            Number value = exp.evaluate(pair.getRight(), code, false);
            if (value == null) {
                config.error("Cannot resolve expression " + exp + " after loading all the source code!");
                return false;
            }
            if (value instanceof Integer) {
                exp.type = Expression.EXPRESSION_INTEGER_CONSTANT;
                exp.integerConstant = value.intValue();
            } else {
                exp.type = Expression.EXPRESSION_DOUBLE_CONSTANT;
                exp.doubleConstant = value.doubleValue();
            }
        }

        return true;
    }

    public SourceFile parseSourceFile(String fileName, CodeBase code, SourceFile parent,
            SourceStatement parentInclude) {
        if (code.getSourceFile(fileName) != null) {
            config.warn("Re-entering into "+fileName+" ignored...");
            return null;
        }

        SourceFile f = new SourceFile(fileName, parent, parentInclude, config);
        if (parent == null)
            code.setMain(f);
        code.addSourceFile(f);
        config.preProcessor.pushState();
        try {
            if (parseSourceFileInternal(f, code, config)) {
                config.preProcessor.popState();
                return f;
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
        List<String> unfilteredTokens = new ArrayList<>();

        SourceLine sl = config.preProcessor.expandMacros();
        if (sl == null) {
            String line = null;
            if (br != null) line = br.readLine();
            if (line == null) return null;
            file_linenumber++;
            sl = new SourceLine(line, f, file_linenumber);
        }

        Tokenizer.tokenize(sl.line, unfilteredTokens);
        if (!unfilteredTokens.isEmpty() && unfilteredTokens.get(unfilteredTokens.size()-1).equals(",")) {
            // unfinished line, get the next one!
            List<String> tokens2 = new ArrayList<>();
            Pair<SourceLine, Integer> tmp = getNextLine(br, sl.source, file_linenumber, tokens2);
            if (tmp != null) {
                sl.line += "\n" + tmp.getLeft().line;
                unfilteredTokens.addAll(tokens2);
                file_linenumber = tmp.getRight();
            }
        }

        // remove multi-line comments
        for(String token:unfilteredTokens) {
            if (withinMultilineComment) {
                if (Tokenizer.isMultiLineCommentEnd(token)) {
                    withinMultilineComment = false;
                }
            } else {
                if (Tokenizer.isMultiLineCommentStart(token)) {
                    withinMultilineComment = true;
                } else {
                    tokens.add(token);
                }
            }
        }

        return Pair.of(sl, file_linenumber);
    }


    boolean parseSourceFileInternal(SourceFile f, CodeBase code, MDLConfig config) throws IOException
    {
        config.trace("Parsing "+f.fileName+"...");

        try (BufferedReader br = Resources.asReader(f.fileName)) {
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
                file_lineNumber = tmp.getRight();
                SourceLine sl = tmp.getLeft();
                // int line_lineNumber = file_lineNumber;
                // if (sl.lineNumber != null) line_lineNumber = tmp.getLeft().lineNumber;

                if (config.preProcessor.withinMacroDefinition()) {
                    List<SourceStatement> newStatements =  config.preProcessor.parseMacroLine(tokens, sl, f, code, config);
                    if (newStatements == null) {
                        return false;
                    } else {
                        for(SourceStatement s:newStatements) {
                            if (config.eagerMacroEvaluation) {
                                List<SourceStatement> l2 = config.preProcessor.handleStatement(sl, s, f, code, false);
                                if (l2 == null) {
                                    f.addStatement(s);
                                } else {
                                    for(SourceStatement s2:l2) {
                                        f.addStatement(s2);
                                    }
                                }
                            } else {
                                f.addStatement(s);                            
                            }
                        }
                    }
                } else {
                    List<SourceStatement> l = config.lineParser.parse(tokens, sl, f, code, config);
                    if (l == null) return false;
                    for(SourceStatement s:l) {
                        List<SourceStatement> l2 = config.preProcessor.handleStatement(sl, s, f, code, false);
                        if (l2 == null) {
                            f.addStatement(s);
                        } else {
                            for(SourceStatement s2:l2) {
                                f.addStatement(s2);
                            }
                        }
                    }
                }
            }
        }
    }


    public boolean expandAllMacros(CodeBase code) throws IOException
    {
        List<SourceFile> l = new ArrayList<>();
        l.addAll(code.getSourceFiles());

        int n_expanded;
        int n_failed;

        do {
            n_expanded = 0;
            n_failed = 0;
            for (SourceFile f : l) {
                Pair<Integer,Integer> expanded = expandAllMacros(f, code);
                if (expanded == null) return false;
                n_expanded += expanded.getLeft();
                n_failed += expanded.getRight();
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
            SourceStatement s_macro = f.getStatements().get(i);

            if (s_macro.type == SourceStatement.STATEMENT_MACROCALL) {
                // expand macro!
                config.trace("expandAllMacros: Expanding macro: " + s_macro.macroCallName != null ? s_macro.macroCallName : s_macro.macroCallMacro.name);

                List<SourceStatement> l2 = config.preProcessor.handleStatement(s_macro.sl, s_macro, f, code, true);
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

                // Parse the new lines (which could, potentially trigger other macros!):
                while(true) {
                    List<String> tokens = new ArrayList<>();
                    Pair<SourceLine, Integer> tmp = getNextLine(null, f, s_macro.sl.lineNumber, tokens);
                    if (tmp == null) {
                        if (config.preProcessor.withinMacroDefinition()) {
                            SourceMacro macro = config.preProcessor.getCurrentMacro();
                            SourceLine macroLine = macro.lines.iterator().next(); // (first macro line)
                            config.error(
                                    "File " + f.fileName + " ended while inside a macro definition of \"" + macro.name + "\" "
                                    + "at #" + macroLine.lineNumber + ": " + macroLine.line);
                            return null;
                        }
                        break;
                    }
                    SourceLine sl = tmp.getLeft();
                    //int lineNumber = s_macro.lineNumber;
                    //if (tmp.getLeft().lineNumber != null) lineNumber = tmp.getLeft().lineNumber;
                    if (config.preProcessor.withinMacroDefinition()) {
                        List<SourceStatement> newStatements =  config.preProcessor.parseMacroLine(tokens, sl, f, code, config);
                        if (newStatements == null) {
                            return null;
                        } else {
                            for(SourceStatement s:newStatements) {
                                f.addStatement(insertionPoint, s);
                                insertionPoint++;
                            }
                        }
                    } else {
                        List<SourceStatement> l = config.lineParser.parse(tokens, sl, f, code, config);
                        if (l == null) return null;
                        for(SourceStatement s:l) {
                            List<SourceStatement> l3 = config.preProcessor.handleStatement(sl, s, f, code, true);
                            if (l3 == null) {
                                f.addStatement(insertionPoint, s);
                                insertionPoint++;
                            } else {
                                for(SourceStatement s3:l3) {
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
            for(SourceStatement s:f.getStatements()) {
                s.resolveLocalLabels(code);
            }
        }
 
        return Pair.of(n_expanded, n_failed);
    }


}

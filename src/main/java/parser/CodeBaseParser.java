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

    public boolean isMacro(String name, CodeBase code) {
        if (config.preProcessor == null)
            return false;
        return config.preProcessor.isMacro(name, code);
    }

    public boolean parseMainSourceFile(String fileName, CodeBase code) throws IOException {
        if (parseSourceFile(fileName, code, null, null) == null) return false;

        // Expand all macros that were not expanded initially:
        if (!expandAllMacros(code)) {
            config.error("Problem expanding macros after loading all the source code!");
            return false;
        }

        if (config.dialectParser != null) config.dialectParser.performAnyFinalActions(code);
        for(Pair<Expression, SourceStatement> pair:expressionsToReplaceByValueAtTheEnd) {
            Expression exp = pair.getLeft();
            Integer value = exp.evaluate(pair.getRight(), code, false);
            if (value == null) {
                config.error("Cannot resolve expression " + exp + " after loading all the source code!");
                return false;
            }
            exp.type = Expression.EXPRESSION_NUMERIC_CONSTANT;
            exp.numericConstant = value;
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
        try {
            if (parseSourceFileInternal(f, code, config))
                return f;
        } catch (Exception e) {
            config.error("Problem parsing file " + fileName + ": " + e);
            for(Object st:e.getStackTrace()) {
                config.error("    " + st);
            }
        }
        return null;
    }

    // Returns: <<line,lineNumber>, file_linenumber>
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
                String line = tmp.getLeft().line;
                int line_lineNumber = file_lineNumber;
                if (tmp.getLeft().lineNumber != null) line_lineNumber = tmp.getLeft().lineNumber;

                if (config.preProcessor.withinMacroDefinition()) {
                    List<SourceStatement> newStatements =  config.preProcessor.parseMacroLine(tokens, line, line_lineNumber, f, code, config);
                    if (newStatements == null) {
                        return false;
                    } else {
                        for(SourceStatement s:newStatements) {
                            f.addStatement(s);
                        }
                    }
                } else {
                    List<SourceStatement> l = config.lineParser.parse(tokens, line, line_lineNumber, f, code, config);
                    if (l == null) return false;
                    for(SourceStatement s:l) {
                        if (!s.isEmpty()) {
                            if (!config.preProcessor.handleStatement(line, line_lineNumber, s, f, code, false)) {
                                f.addStatement(s);
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

                if (!config.preProcessor.handleStatement("", s_macro.lineNumber, s_macro, f, code, true)) {
                    config.debug("Cannot yet expand macro "+s_macro.macroCallName+" in "+s_macro.source.fileName+", " + s_macro.lineNumber);
                    n_failed++;
                    continue;
                } else {
                    n_expanded++;
                    f.getStatements().remove(i);
                }


                // We need to reset the addresses, as when we expand a macro, these can all change!
                code.resetAddresses();

                // Parse the new lines (which could, potentially trigger other macros!):
                int insertionPoint = i;
                while(true) {
                    List<String> tokens = new ArrayList<>();
                    Pair<SourceLine, Integer> tmp = getNextLine(null, f, s_macro.lineNumber, tokens);
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
                    String line = tmp.getLeft().line;
                    int lineNumber = s_macro.lineNumber;
                    if (tmp.getLeft().lineNumber != null) lineNumber = tmp.getLeft().lineNumber;
                    if (config.preProcessor.withinMacroDefinition()) {
                        List<SourceStatement> newStatements =  config.preProcessor.parseMacroLine(tokens, line, lineNumber, f, code, config);
                        if (newStatements == null) {
                            return null;
                        } else {
                            for(SourceStatement s:newStatements) {
                                f.addStatement(insertionPoint, s);
                                insertionPoint++;
                            }
                        }
                    } else {
                        List<SourceStatement> l = config.lineParser.parse(tokens, line, lineNumber, f, code, config);
                        if (l == null) return null;
                        for(SourceStatement s:l) {
                            if (!s.isEmpty()) {
                                if (!config.preProcessor.handleStatement(line, lineNumber, s, f, code, true)) {
                                    f.addStatement(insertionPoint, s);
                                    insertionPoint++;
                                }
                            }
                        }
                    }
                }

                i--;
            }
        }
        return Pair.of(n_expanded, n_failed);
    }


}

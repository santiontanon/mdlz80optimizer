/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import org.apache.commons.lang3.tuple.Pair;
import util.Resources;

public class CodeBaseParser {
    MDLConfig config;

    public CodeBaseParser(MDLConfig a_config) {
        config = a_config;
    }

    public boolean isMacro(String name, CodeBase code) {
        if (config.preProcessor == null)
            return false;
        return config.preProcessor.isMacro(name, code);
    }

    public SourceFile parseMainSourceFile(String fileName, CodeBase code) throws IOException {
        SourceFile main = parseSourceFile(fileName, code, null, null);
        if (main == null)
            return null;

        // Expand all macros that were not expanded initially:
        if (!expandAllMacros(code)) {
            config.error("Problem expanding macros after loading all the source code!");
            return null;
        }

        return main;
    }

    public SourceFile parseSourceFile(String fileName, CodeBase code, SourceFile parent,
            SourceStatement parentInclude) {
        if (code.getSourceFile(fileName) != null) {
            config.warn("Re-entering into " + fileName + " ignored...");
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
            config.error("Problem parsing file " + fileName);
            e.printStackTrace();
        }
        return null;
    }

    // Returns: <<line,lineNumber>, file_linenumber>
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

        Tokenizer.tokenize(sl.line, tokens);
        if (!tokens.isEmpty() && tokens.get(tokens.size()-1).equals(",")) {
            // unfinished line, get the next one!
            List<String> tokens2 = new ArrayList<>();
            Pair<SourceLine, Integer> tmp = getNextLine(br, sl.source, file_linenumber, tokens2);
            if (tmp != null) {
                sl.line += "\n" + tmp.getLeft().line;
                tokens.addAll(tokens2);
                file_linenumber = tmp.getRight();
            }
        }

        return Pair.of(sl, file_linenumber);
    }


    boolean parseSourceFileInternal(SourceFile f, CodeBase code, MDLConfig config) throws IOException
    {
        config.debug("Parsing " + f.fileName + "...");

        try (BufferedReader br = Resources.asReader(f.fileName)) {
            int file_lineNumber = 0;
            while(true) {
                List<String> tokens = new ArrayList<>();
                Pair<SourceLine, Integer> tmp = getNextLine(br, f, file_lineNumber, tokens);
                if (tmp == null) {
                    if (config.preProcessor.withinMacroDefinition()) {
                        config.error("File " +f.fileName+ " ended while inside a macro definition: " + config.preProcessor.getCurrentMacro().name);
                        return false;
                    }
                    return true;
                }
                file_lineNumber = tmp.getRight();
                String line = tmp.getLeft().line;
                int line_lineNumber = file_lineNumber;
                if (tmp.getLeft().lineNumber != null) line_lineNumber = tmp.getLeft().lineNumber;

                if (config.preProcessor.withinMacroDefinition()) {
                    if (!config.preProcessor.parseMacroLine(tokens, line, line_lineNumber, f, code, config)) return false;
                } else {
                    SourceStatement s = config.lineParser.parse(tokens, line, line_lineNumber, f, code, config);
                    if (s == null) return false;
                    if (!s.isEmpty()) {
                        if (!config.preProcessor.handleStatement(line, line_lineNumber, s, f, code, false)) {
                            f.addStatement(s);
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
        
        for (SourceFile f : l) {
            if (!expandAllMacros(f, code)) return false;
        }
        
        if (code.getSourceFiles().size() > l.size()) {
            // there are more files, we need to expand macros again!
            return expandAllMacros(code);
        }
        return true;
    }


    public boolean expandAllMacros(SourceFile f, CodeBase code) throws IOException
    {
        for(int i = 0;i<f.getStatements().size();i++) {
            SourceStatement s_macro = f.getStatements().get(i);
            if (s_macro.type == SourceStatement.STATEMENT_MACROCALL) {
                // expand macro!
                if (s_macro.macroCallName != null) {
                    config.debug("expandAllMacros: Expanding macro: " + s_macro.macroCallName);
                } else {
                    config.debug("expandAllMacros: Expanding macro: " + s_macro.macroCallMacro.name);
                }

                if (!config.preProcessor.handleStatement("", s_macro.lineNumber, s_macro, f, code, true)) {
                    config.error("Cannot expand macro " + s_macro.macroCallName + " in " + s_macro.source.fileName + ", " + s_macro.lineNumber);
                    return false;
                }

                f.getStatements().remove(i);

                // Parse the new lines (which could, potentially trigger other macros!):
                int insertionPoint = i;
                while(true) {
                    List<String> tokens = new ArrayList<>();
                    Pair<SourceLine, Integer> tmp = getNextLine(null, f, s_macro.lineNumber, tokens);
                    if (tmp == null) {
                        if (config.preProcessor.withinMacroDefinition()) {
                            config.error("File " + f.fileName + " ended while inside a macro definition");
                            return false;
                        }
                        break;
                    }
                    String line = tmp.getLeft().line;
                    int lineNumber = s_macro.lineNumber;
                    if (tmp.getLeft().lineNumber != null) lineNumber = tmp.getLeft().lineNumber;
                    if (config.preProcessor.withinMacroDefinition()) {
                        if (!config.preProcessor.parseMacroLine(tokens, line, lineNumber, f, code, config)) return false;
                    } else {
                        SourceStatement s = config.lineParser.parse(tokens, line, lineNumber, f, code, config);
                        if (s == null) return false;
                        if (!s.isEmpty()) {
                            if (!config.preProcessor.handleStatement(line, lineNumber, s, f, code, true)) {
                                f.addStatement(insertionPoint, s);
                                insertionPoint++;
                            }
                        }
                    }
                }
                i--;
            }
        }
        return true;
    }


}

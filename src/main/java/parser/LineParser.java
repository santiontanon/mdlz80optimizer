/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import util.Resources;

public class LineParser {

    public String KEYWORD_ORG = "org";
    public String KEYWORD_INCLUDE = "include";
    public String KEYWORD_INCBIN = "incbin";
    public String KEYWORD_EQU = "equ";
    public String KEYWORD_DB = "db";
    public String KEYWORD_DW = "dw";
    public String KEYWORD_DD = "dd";
    public String KEYWORD_DS = "ds";
    HashMap<String, String> keywordSynonyms = new HashMap<>();
    public List<String> keywordsHintingALabel = new ArrayList<>();

    // If this is set to true then "ds 1" is the same as "ds virtual 1"
    public boolean defineSpaceVirtualByDefault = false;
    public boolean allowEmptyDB_DW_DD_definitions = false;

    MDLConfig config;
    CodeBaseParser codeBaseParser;

    // for local labels:
    String labelPrefix = "";
    List<String> labelPrefixStack = new ArrayList<>();


    public LineParser(MDLConfig a_config, CodeBaseParser a_codeBaseParser) {
        config = a_config;
        codeBaseParser = a_codeBaseParser;

        keywordsHintingALabel.add(KEYWORD_EQU);
    }


    public void addKeywordSynonym(String synonym, String kw) {
        keywordSynonyms.put(synonym, kw);
    }


    public boolean isKeyword(String token, String kw) {
        if (token.equalsIgnoreCase(kw)) {
            return true;
        }
        token = token.toLowerCase();
        if (keywordSynonyms.containsKey(token)
                && keywordSynonyms.get(token).equalsIgnoreCase(kw)) {
            return true;
        }
        return false;
    }


    public void pushLabelPrefix(String a_lp) {
        labelPrefixStack.add(0, labelPrefix);
        labelPrefix = a_lp;
    }


    public void popLabelPrefix() {
        labelPrefix = labelPrefixStack.remove(0);
    }


    public String getLabelPrefix()
    {
        return labelPrefix;
    }


    public String newSymbolName(String name, Expression value) {
        if (config.dialectParser != null) {
            return config.dialectParser.newSymbolName(name, value);
        } else {
            return name;
        }
    }


    public SourceStatement parse(List<String> tokens, String line, int lineNumber,
            SourceFile f, CodeBase code, MDLConfig config) {
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, f, lineNumber, null);

        if (!parseInternal(tokens, line, lineNumber, s, f, code)) {
            return null;
        }
        return s;
    }


    boolean parseInternal(List<String> tokens, String line, int lineNumber, SourceStatement s, SourceFile source, CodeBase code) {
        if (!parseLabel(tokens, line, lineNumber, s, source, code, true)) return false;

        if (tokens.isEmpty()) return true;
        String token = tokens.get(0);

        if (isKeyword(token, KEYWORD_ORG)) {
            tokens.remove(0);
            return parseOrg(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, KEYWORD_INCLUDE)) {
            tokens.remove(0);
            return parseInclude(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, KEYWORD_INCBIN)) {
            tokens.remove(0);
            return parseIncbin(tokens, line, lineNumber, s, source, code);
        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_EQU)) {
            tokens.remove(0);
            return parseEqu(tokens, line, lineNumber, s, source, code, true);
        } else if (tokens.size() >= 1
                && (isKeyword(token, KEYWORD_DB)
                || isKeyword(token, KEYWORD_DW)
                || isKeyword(token, KEYWORD_DD))) {
            tokens.remove(0);
            return parseData(tokens, token, line, lineNumber, s, source, code);

        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_DS)) {
            tokens.remove(0);
            return parseDefineSpace(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, SourceMacro.MACRO_MACRO)) {
            tokens.remove(0);
            return parseMacroDefinition(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, SourceMacro.MACRO_ENDM)) {
            config.error(SourceMacro.MACRO_ENDM + " keyword found outside of a macro at " + source.fileName + ", "
                    + lineNumber + ": " + line);
            return false;

        } else if (config.dialectParser != null && config.dialectParser.recognizeIdiom(tokens)) {
            return config.dialectParser.parseLine(tokens, line, lineNumber, s, source, code);
        } else if (Tokenizer.isSymbol(token)) {
            // try to parseArgs it as an assembler instruction or macro call:
            tokens.remove(0);
            if (config.opParser.isOpName(token)) {
                return parseZ80Op(tokens, token, line, lineNumber, s, source, code);
            } else {
                return parseMacroCall(tokens, token, line, lineNumber, s, source, code);
            }
        } else {
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }


    public boolean parseLabel(List<String> tokens, String line, int lineNumber, SourceStatement s, SourceFile source, CodeBase code, boolean defineInCodeBase) {
        if (tokens.isEmpty()) return true;

        String token = tokens.get(0);

        if (tokens.size() >= 2
                && Tokenizer.isSymbol(token)
                && tokens.get(1).equals(":")) {
            Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config);

            if (tokens.size() >= 3) {
                tokens.remove(0);
                tokens.remove(0);

                String symbolName = newSymbolName(labelPrefix + token, exp);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) return false;
                }
            } else {
                tokens.remove(0);
                tokens.remove(0);

                String symbolName = newSymbolName(labelPrefix + token, exp);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) return false;
                }
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            }
        } else if (Tokenizer.isSymbol(token)) {
            if (line.startsWith(token) && (tokens.size() == 1 || tokens.get(1).startsWith(";"))) {
                // it is just a label without colon:
                if (config.warningLabelWithoutColon) {
                    config.warn("Style suggestion", s.source.fileName, s.lineNumber, 
                            "Label "+token+" defined without a colon.");
                }
                Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config);
                int address = exp.evaluate(s, code, false);
                tokens.remove(0);

                String symbolName = newSymbolName(labelPrefix + token, exp);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, address, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) return false;
                }
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            } else if (tokens.size() >= 3) {
                boolean isLabel = false;
                for(String keyword:keywordsHintingALabel) {
                    if (isKeyword(tokens.get(1), keyword)) {
                        isLabel = true;
                        break;
                    }
                }
                if (isLabel) {
                    if (config.warningLabelWithoutColon) {
                        config.warn("Style suggestion", s.source.fileName, s.lineNumber, 
                                "Label "+token+" defined without a colon.");
                    }
                    tokens.remove(0);
                    String symbolName = newSymbolName(labelPrefix + token, null);
                    if (symbolName == null) {
                        config.error("Problem defining symbol " + labelPrefix + token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                        return false;
                    }
                    SourceConstant c = new SourceConstant(symbolName, null, null, s);
                    s.type = SourceStatement.STATEMENT_NONE;
                    s.label = c;
                    if (defineInCodeBase) {
                        if (!code.addSymbol(c.name, c)) return false;
                    }
                }
            }
        }

        return true;
    }


    public boolean parseRestofTheLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source) {
        if (tokens.isEmpty()) {
            return true;
        }
        if (tokens.size() == 1 && tokens.get(0).startsWith(";")) {
            s.comment = tokens.get(0);
            return true;
        }

        config.error("Cannot parse line " + source.fileName + ", "
                + lineNumber + ": " + line);
        return false;
    }


    public boolean parseOrg(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        Expression exp = config.expressionParser.parse(tokens, code);
        if (exp == null) {
            config.error("Cannot parse line " + source.fileName + ", "
                    + lineNumber + ": " + line);
            return false;
        } else {
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }


    public boolean parseInclude(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        if (tokens.size() >= 1) {
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                String rawFileName = Tokenizer.stringValue(token);

                // recursive include file:
                String path = resolveIncludePath(rawFileName, source);
                SourceFile includedSource = codeBaseParser.parseSourceFile(path, code, source, s);
                if (includedSource == null) {
                    config.error("Problem including file at " + source.fileName + ", "
                            + lineNumber + ": " + line);
                    return false;
                } else {
                    s.type = SourceStatement.STATEMENT_INCLUDE;
                    s.include = includedSource;
                    return parseRestofTheLine(tokens, line, lineNumber, s, source);
                }
            }
        }
        config.error("Cannot parse line " + source.fileName + ", "
                + lineNumber + ": " + line);
        return false;
    }


    public boolean parseIncbin(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        if (tokens.size() >= 1) {
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                String rawFileName = Tokenizer.stringValue(token);
                String path = resolveIncludePath(rawFileName, source);
                if (path == null) {
                    config.error("Incbin file " + rawFileName + " does not exist in " + source.fileName + ", "
                            + lineNumber + ": " + line);
                    return false;
                }
                s.type = SourceStatement.STATEMENT_INCBIN;
                s.incbin = path;
                s.incbinOriginalStr = rawFileName;
                File f = new File(path);
                if (!f.exists()) {
                    config.error("Incbin file " + rawFileName + " does not exist in " + source.fileName + ", "
                            + lineNumber + ": " + line);
                    return false;
                }
                s.incbinSize = (int) f.length();
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            }
        }
        config.error("Cannot parse line " + source.fileName + ", "
                + lineNumber + ": " + line);
        return false;
    }


    public boolean parseEqu(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code, boolean defineInCodeBase) {
        if (s.label == null) {
            config.error("Equ without label in line " + source.fileName + ", "
                    + lineNumber + ": " + line);
            return false;
        }
        Expression exp = config.expressionParser.parse(tokens, code);
        if (exp == null) {
            config.error("Cannot parse line " + source.fileName + ", "
                    + lineNumber + ": " + line);
            return false;
        }
        s.type = SourceStatement.STATEMENT_CONSTANT;
        s.label.exp = exp;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseData(List<String> tokens, String label,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<Expression> data = new ArrayList<>();
        boolean done = false;
        if (allowEmptyDB_DW_DD_definitions) {
            if (tokens.isEmpty() || tokens.get(0).startsWith(";")) {
                data.add(Expression.constantExpression(0, config));
                done = true;
            }
        }
        while (!done) {
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
            } else {
                data.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                done = true;
            }
        }

        if (isKeyword(label, KEYWORD_DB)) {
            s.type = SourceStatement.STATEMENT_DATA_BYTES;
        } else if (isKeyword(label, KEYWORD_DW)) {
            s.type = SourceStatement.STATEMENT_DATA_WORDS;
        } else {
            s.type = SourceStatement.STATEMENT_DATA_DOUBLE_WORDS;
        }
        s.data = data;

        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseDefineSpace(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        boolean virtual = false;
        if (tokens.get(0).equalsIgnoreCase("virtual")) {
            tokens.remove(0);
            virtual = true;
        }
        if (defineSpaceVirtualByDefault) {
            virtual = true;
        }
        if (virtual) {
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
            }
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp;
            s.space_value = null;
        } else {
            // In this case, "ds" is just a short-hand for "db" with repeated values:
            Expression exp_amount = config.expressionParser.parse(tokens, code);
            Expression exp_value;
            if (exp_amount == null) {
                config.error("Cannot parse line " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
            }
            if (!tokens.isEmpty() && tokens.get(0).startsWith(",")) {
                tokens.remove(0);
                exp_value = config.expressionParser.parse(tokens, code);
                if (exp_value == null) {
                    config.error("Cannot parse line " + source.fileName + ", "
                            + lineNumber + ": " + line);
                    return false;
                }
            } else {
                exp_value = Expression.constantExpression(0, config);
            }

            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp_amount;
            s.space_value = exp_value;
        }

        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseZ80Op(List<String> tokens, String opName,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<Expression> arguments = new ArrayList<>();
        while (!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) {
                break;
            }
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
            } else {
                arguments.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }

        CPUOp op = config.opParser.parseOp(opName, arguments, s, code);
        if (op == null) {
            config.error("No op spec matches with operator in line " + source.fileName + ", "
                    + lineNumber + ": " + line);
            return false;
        }

        s.type = SourceStatement.STATEMENT_CPUOP;
        s.op = op;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseMacroDefinition(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        // Marks that all the lines that come after this, and until ENDM,
        // are part of a macro, and should not yet be parsed:
        if (s.label == null) {
            config.error("Cannot parse line " + source.fileName + ", "
                    + lineNumber + ": " + line);
            return false;
        }

        // parseArgs arguments:
        List<String> args = new ArrayList<>();
        List<Expression> defaultValues = new ArrayList<>();
        while (tokens.size() >= 2 && tokens.get(0).equals("?")) {
            tokens.remove(0);
            args.add(tokens.remove(0));
            if (!tokens.isEmpty() && tokens.get(0).equals("=")) {
                // default value:
                tokens.remove(0);
                Expression defaultValue = config.expressionParser.parse(tokens, code);
                if (defaultValue == null) {
                    config.error("Cannot parse default value in line " + source.fileName + ", "
                            + lineNumber + ": " + line);
                    return false;
                }
                defaultValues.add(defaultValue);
            } else {
                defaultValues.add(null);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            }
        }

        s.type = SourceStatement.STATEMENT_MACRO;
        s.macroDefinitionArgs = args;
        s.macroDefinitionDefaults = defaultValues;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseMacroCall(List<String> tokens, String macroName,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<Expression> arguments = new ArrayList<>();
        while (!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) {
                break;
            }
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", "
                        + lineNumber + ": " + line);
                return false;
            } else {
                arguments.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }

        s.macroCallName = macroName;
        s.macroCallArguments = arguments;
        s.type = SourceStatement.STATEMENT_MACROCALL;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public String resolveIncludePath(String rawFileName, SourceFile source) {

        // Relative to current directory
        if (Resources.exists(rawFileName)) {
            config.debug("Included file " + rawFileName + " found relative to current directory");
            return rawFileName;
        }

        // Relative to original source file
         String sourcePath = source.getPath();
        if (StringUtils.isNotBlank(sourcePath)) {
            // santi: Do NOT change to "FilenameUtils.concat", that function assumes that the first argument
            // is an absolute directory, which in different configurations cannot be ensured to be true.
            // for example when calling mdl like: java -jar mdl.jar ../project/src/main.asm -I ../project2/src
            final String relativePath = pathConcat(sourcePath, rawFileName);
            if (Resources.exists(relativePath)) {
                config.debug("Included file " + rawFileName + " found relative to original source file");
                return relativePath;
            }
        }

        // Relative to any include directory
        for (File includePath : config.includeDirectories) {
            // santi: Do NOT change to "FilenameUtils.concat", that function assumes that the first argument
            // is an absolute directory, which in different configurations cannot be ensured to be true.            
            final String relativePath = pathConcat(includePath.getAbsolutePath(), rawFileName);
            if (Resources.exists(relativePath)) {
                config.debug("Included file " + rawFileName + " found relative to include path " + includePath);
                return relativePath;
            }
        }

        config.error("Cannot find include file " + rawFileName);
        return null;
    }
    
    
    String pathConcat(String path, String fileName)
    {
        if (path.endsWith(File.separator)) {
            return path + fileName;
        } else {
            return path + File.separator + fileName;
        }
    }
    

}

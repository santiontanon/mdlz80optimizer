/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import util.Resources;
import code.CodeBase;
import code.CPUOp;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

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


    MDLConfig config;
    CodeBaseParser codeBaseParser;

    // for local labels:
    String labelPrefix = "";
    List<String> labelPrefixStack = new ArrayList<>();

    public LineParser(MDLConfig a_config, CodeBaseParser a_codeBaseParser)
    {
        config = a_config;
        codeBaseParser = a_codeBaseParser;
    }


    public void addKeywordSynonym(String synonym, String kw)
    {
        keywordSynonyms.put(synonym, kw);
    }


    public boolean isKeyword(String token, String kw)
    {
        if (token.equalsIgnoreCase(kw)) return true;
        if (keywordSynonyms.containsKey(token) &&
            keywordSynonyms.get(token).equalsIgnoreCase(kw)) return true;
        return false;
    }


    public void pushLabelPrefix(String a_lp)
    {
        labelPrefixStack.add(0,labelPrefix);
        labelPrefix = a_lp;
    }


    public void popLabelPrefix()
    {
        labelPrefix = labelPrefixStack.remove(0);
    }


    public String newSymbolName(String name, Expression value)
    {
        if (config.dialectParser != null) {
            return config.dialectParser.newSymbolName(name, value);
        } else {
            return name;
        }
    }


    public SourceStatement parse(List<String> tokens, String line, int lineNumber,
            SourceFile f, CodeBase code, MDLConfig config) throws Exception
    {
        // SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, source, lineNumber, code.getAddress());
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, f, lineNumber, null);

        if (!parseInternal(tokens, line, lineNumber, s, f, code)) return null;
        return s;
    }


    boolean parseInternal(List<String> tokens, String line, int lineNumber, SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.isEmpty()) return true;

        String token = tokens.get(0);

        // The very first thing is to check if there is a label:
        if (tokens.size() >= 2 &&
            Tokenizer.isSymbol(token) &&
            tokens.get(1).equals(":")) {
            Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code);

            if (tokens.size() >= 3) {
                if (!isKeyword(tokens.get(2), KEYWORD_EQU)) {
                    tokens.remove(0);
                    tokens.remove(0);

                    String symbolName = newSymbolName(labelPrefix+token, exp);
                    if (symbolName == null) {
                        config.error("Problem defining symbol " + labelPrefix+token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                        return false;
                    }
                    SourceConstant c = new SourceConstant(symbolName, null, exp, s);
                    s.type = SourceStatement.STATEMENT_NONE;
                    s.label = c;
                    code.addSymbol(c.name, c);
                    token = tokens.get(0);
                }
            } else {
                tokens.remove(0);
                tokens.remove(0);

                String symbolName = newSymbolName(labelPrefix+token, exp);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix+token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                code.addSymbol(c.name, c);
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            }
        } else if (Tokenizer.isSymbol(token)) {
            if (line.startsWith(token)) {
                if (tokens.size() == 1 || tokens.get(1).startsWith(";")) {
                    // it is just a label without colon:
                    if (config.warningLabelWithoutColon) {
                        config.warn("Label defined without a colon in " +
                                source.fileName + ", " + lineNumber + ": " + line);
                    }
                    Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code);
                    int address = exp.evaluate(s, code, false);
                    tokens.remove(0);

                    String symbolName = newSymbolName(labelPrefix+token, exp);
                    if (symbolName == null) {
                        config.error("Problem defining symbol " + labelPrefix+token + " in " + source.fileName + ", " + lineNumber + ": " + line);
                        return false;
                    }
                    SourceConstant c = new SourceConstant(symbolName, address, exp, s);
                    s.type = SourceStatement.STATEMENT_NONE;
                    s.label = c;
                    code.addSymbol(c.name, c);
                    return parseRestofTheLine(tokens, line, lineNumber, s, source);
                } else if (tokens.size() >= 3 && isKeyword(tokens.get(1), "equ")) {
                    // equ without a colon (provide warning):
                    if (config.warningLabelWithoutColon) {
                        config.warn("Label defined without a colon in " +
                                source.fileName + ", " + lineNumber + ": " + line);
                    }
                    tokens.remove(0);
                    tokens.remove(0);
                    return parseEqu(tokens, token, line, lineNumber, s, source, code);
                }
            } else if (tokens.size() >= 3 && tokens.get(1).equalsIgnoreCase("equ")) {
                // equ without a colon (provide warning):
                if (config.warningLabelWithoutColon) {
                    config.warn("Label defined without a colon in " +
                            source.fileName + ", " + lineNumber + ": " + line);
                }
                tokens.remove(0);
                tokens.remove(0);
                return parseEqu(tokens, token, line, lineNumber, s, source, code);
            }
        }

        if (isKeyword(token, KEYWORD_ORG)) {
            tokens.remove(0);
            return parseOrg(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, KEYWORD_INCLUDE)) {
            tokens.remove(0);
            return parseInclude(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, KEYWORD_INCBIN)) {
            tokens.remove(0);
            return parseIncbin(tokens, line, lineNumber, s, source, code);

        } else if (tokens.size() >= 4 &&
                   Tokenizer.isSymbol(token) &&
                   tokens.get(1).equals(":") &&
                   isKeyword(tokens.get(2), KEYWORD_EQU)) {
            tokens.remove(0);
            tokens.remove(0);
            tokens.remove(0);
            return parseEqu(tokens, token, line, lineNumber, s, source, code);

        } else if (tokens.size() >= 2 &&
                   (isKeyword(token, KEYWORD_DB) ||
                    isKeyword(token, KEYWORD_DW) ||
                    isKeyword(token, KEYWORD_DD))) {
            tokens.remove(0);
            return parseData(tokens, token, line, lineNumber, s, source, code);

        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_DS)) {
            tokens.remove(0);
            return parseDefineSpace(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, SourceMacro.MACRO_MACRO)) {
            tokens.remove(0);
            return parseMacroDefinition(tokens, line, lineNumber, s, source, code);

        } else if (isKeyword(token, SourceMacro.MACRO_ENDM)) {
            config.error(SourceMacro.MACRO_ENDM + " keyword found outside of a macro at " + source.fileName + ", " +
                         lineNumber + ": " + line);
            return false;

        } else if (config.dialectParser != null && config.dialectParser.recognizeIdiom(tokens)) {
            return config.dialectParser.parseLine(tokens, line, lineNumber, s, source, code);
        } else if (Tokenizer.isSymbol(token)) {
            // try to parse it as an assembler instruction or macro call:
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


    public boolean parseRestofTheLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source)
    {
        if (tokens.isEmpty()) return true;
        if (tokens.size() == 1 && tokens.get(0).startsWith(";")) {
            s.comment = tokens.get(0);
            return true;
        }

        config.error("Cannot parse line " + source.fileName + ", " +
                     lineNumber + ": " + line);
        return false;
    }


    public boolean parseOrg(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        Expression exp = config.expressionParser.parse(tokens, code);
        if (exp == null) {
            config.error("Cannot parse line " + source.fileName + ", " +
                         lineNumber + ": " + line);
            return false;
        } else {
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }


    public boolean parseInclude(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.size() >= 1) {
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                String rawFileName = Tokenizer.stringValue(token);

                // recursive include file:
                String path = resolveIncludePath(rawFileName, source);
                SourceFile includedSource = codeBaseParser.parseSourceFile(path, code, source, s);
                if (includedSource == null) {
                    config.error("Problem including file at " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                } else {
                    s.type = SourceStatement.STATEMENT_INCLUDE;
                    s.include = includedSource;
                    return parseRestofTheLine(tokens, line, lineNumber, s, source);
                }
            }
        }
        config.error("Cannot parse line " + source.fileName + ", " +
                     lineNumber + ": " + line);
        return false;
    }


    public boolean parseIncbin(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.size() >= 1) {
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                String rawFileName = Tokenizer.stringValue(token);
                String path = resolveIncludePath(rawFileName, source);
                if (path == null) {
                    config.error("Incbin file "+rawFileName+" does not exist in " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
                s.type = SourceStatement.STATEMENT_INCBIN;
                s.incbin = path;
                s.incbinOriginalStr = rawFileName;
                File f = new File(path);
                if (!f.exists()) {
                    config.error("Incbin file "+rawFileName+" does not exist in " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
                s.incbinSize = (int)f.length();
                return parseRestofTheLine(tokens, line, lineNumber, s, source);
            }
        }
        config.error("Cannot parse line " + source.fileName + ", " +
                     lineNumber + ": " + line);
        return false;
    }


    public boolean parseEqu(List<String> tokens, String label,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        Expression exp = config.expressionParser.parse(tokens, code);
        if (exp == null) {
            config.error("Cannot parse line " + source.fileName + ", " +
                         lineNumber + ": " + line);
            return false;
        } else {
            Integer value = exp.evaluate(s, code, true);

            String symbolName = newSymbolName(labelPrefix+label, exp);
            if (symbolName == null) {
                config.error("Problem defining symbol " + labelPrefix+label + " in " + source.fileName + ", " + lineNumber + ": " + line);
                return false;
            }
            SourceConstant c = new SourceConstant(symbolName, value, exp, s);
            s.type = SourceStatement.STATEMENT_CONSTANT;
            s.label = c;
            code.addSymbol(c.name, c);
            return parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
    }


    public boolean parseData(List<String> tokens, String label,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        List<Expression> data = new ArrayList<>();
        while(true) {
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return false;
            } else {
                data.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }

        if (label.equalsIgnoreCase("db")) {
            s.type = SourceStatement.STATEMENT_DATA_BYTES;
        } else if (label.equalsIgnoreCase("dw")) {
            s.type = SourceStatement.STATEMENT_DATA_WORDS;
        } else {
            s.type = SourceStatement.STATEMENT_DATA_DOUBLE_WORDS;
        }
        s.data = data;

        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseDefineSpace(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        if (tokens.get(0).equalsIgnoreCase("virtual")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
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
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return false;
            }
            if (!tokens.isEmpty() && !tokens.get(0).startsWith(";")) {
                exp_value = config.expressionParser.parse(tokens, code);
                if (exp_value == null) {
                    config.error("Cannot parse line " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
            } else {
                exp_value = Expression.constantExpression(0);
            }

            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp_amount;
            s.space_value = exp_value;
        }

        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseZ80Op(List<String> tokens, String opName,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        List<Expression> arguments = new ArrayList<>();
        while(!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) break;
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
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
            config.error("No op spec matches with operator in line " + source.fileName + ", " +
                         lineNumber + ": " + line);
            return false;
        }

        s.type = SourceStatement.STATEMENT_CPUOP;
        s.op = op;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseMacroDefinition(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        // Marks that all the lines that come after this, and until ENDM,
        // are part of a macro, and should not yet be parsed:
        if (s.label == null) {
            config.error("Cannot parse line " + source.fileName + ", " +
                         lineNumber + ": " + line);
            return false;
        }

        // parse arguments:
        List<String> args = new ArrayList<>();
        List<Expression> defaultValues = new ArrayList<>();
        while(tokens.size()>=2 && tokens.get(0).equals("?")) {
            tokens.remove(0);
            args.add(tokens.remove(0));
            if (!tokens.isEmpty() && tokens.get(0).equals("=")) {
                // default value:
                tokens.remove(0);
                Expression defaultValue = config.expressionParser.parse(tokens, code);
                if (defaultValue == null) {
                    config.error("Cannot parse default value in line " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
                defaultValues.add(defaultValue);
            } else {
                defaultValues.add(null);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) tokens.remove(0);
        }

        s.type = SourceStatement.STATEMENT_MACRO;
        s.macroDefinitionArgs = args;
        s.macroDefinitionDefaults = defaultValues;
        return parseRestofTheLine(tokens, line, lineNumber, s, source);
    }


    public boolean parseMacroCall(List<String> tokens, String macroName,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
    {
        List<Expression> arguments = new ArrayList<>();
        while(!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) break;
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
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


    public String resolveIncludePath(String rawFileName, SourceFile source)
    {
        // Relative include
        String parentPath = source.getPath();
        String relativeFile = FilenameUtils.concat(parentPath, rawFileName);
        if (Resources.exists(relativeFile)) {
            return relativeFile;
        }

        // Include from include directories...
        String justFileName = FilenameUtils.getName(rawFileName);
        for (String directory : config.includeDirectories) {
            // ...with relative path
            String withPath = FilenameUtils.concat(directory, rawFileName);
            if (Resources.exists(withPath)) {
                return withPath;
            }
            // ...without path
            String withoutPath = FilenameUtils.concat(directory, justFileName);
            if (Resources.exists(withoutPath)) {
                return withoutPath;
            }
        }

        config.error("Cannot find include file " + rawFileName);
        return null;
    }

}

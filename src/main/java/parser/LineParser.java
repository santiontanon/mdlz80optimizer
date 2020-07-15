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
    public boolean allowIncludesWithoutQuotes = false;
    
    // sjasm defines macros like: "macro macroname arg1,...,agn" instead of "macroname: macro arg1,...,argn":
    public boolean macroNameIsFirstArgumentOfMacro = false;
    public boolean allowNumberLabels = false;   // also for sjasm (for "reusable" labels)

    MDLConfig config;
    CodeBaseParser codeBaseParser;

    // for local labels:
    String labelPrefix = "";
    List<String> labelPrefixStack = new ArrayList<>();


    public LineParser(MDLConfig a_config, CodeBaseParser a_codeBaseParser) {
        config = a_config;
        codeBaseParser = a_codeBaseParser;

        keywordsHintingALabel.add(KEYWORD_EQU);
        keywordsHintingALabel.add(KEYWORD_DB);
        keywordsHintingALabel.add(KEYWORD_DW);
        keywordsHintingALabel.add(KEYWORD_DD);
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


    public List<SourceStatement> parse(List<String> tokens, SourceLine sl,
            SourceFile f, CodeBase code, MDLConfig config) {
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, sl, f, null);
        List<SourceStatement> l = parseInternal(tokens, sl, s, f, code);
        return l;
    }


    List<SourceStatement> parseInternal(List<String> tokens, SourceLine sl, SourceStatement s, SourceFile source, CodeBase code) {
        if (!parseLabel(tokens, sl, s, source, code, true)) return null;
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);

        if (tokens.isEmpty()) return l;
        String token = tokens.get(0);

        if (isKeyword(token, KEYWORD_ORG)) {
            tokens.remove(0);
            if (parseOrg(tokens, sl, s, source, code)) return l;
        } else if (isKeyword(token, KEYWORD_INCLUDE)) {
            tokens.remove(0);
            if (parseInclude(tokens, sl, s, source, code)) return l;

        } else if (isKeyword(token, KEYWORD_INCBIN)) {
            tokens.remove(0);
            if (parseIncbin(tokens, sl, s, source, code)) return l;
        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_EQU)) {
            tokens.remove(0);
            if (parseEqu(tokens, sl, s, source, code, true)) return l;
        } else if (tokens.size() >= 1
                && (isKeyword(token, KEYWORD_DB)
                || isKeyword(token, KEYWORD_DW)
                || isKeyword(token, KEYWORD_DD))) {
            tokens.remove(0);
            if (parseData(tokens, token, sl, s, source, code)) return l;

        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_DS)) {
            tokens.remove(0);
            if (parseDefineSpace(tokens, sl, s, source, code)) return l;

        } else if (isKeyword(token, config.preProcessor.MACRO_MACRO)) {
            tokens.remove(0);
            if (parseMacroDefinition(tokens, sl, s, source, code)) return l;

        } else if (isKeyword(token, config.preProcessor.MACRO_ENDM)) {
            config.error(config.preProcessor.MACRO_ENDM + " keyword found outside of a macro at " + source.fileName + ", "
                    + sl.fileNameLineString());
            return null;

        } else if (config.dialectParser != null && config.dialectParser.recognizeIdiom(tokens)) {
            // this one might return one or more statements:
            return config.dialectParser.parseLine(tokens, sl, s, source, code);
        } else if (Tokenizer.isSymbol(token)) {
            // try to parseArgs it as an assembler instruction or macro call:
            tokens.remove(0);
            if (config.opParser.isOpName(token)) {
                if (parseCPUOp(tokens, token, sl, s, source, code)) return l;
            } else {
                if (parseMacroCall(tokens, token, sl, s, source, code)) return l;
            }
        } else {
            if (parseRestofTheLine(tokens, sl, s, source)) return l;
        }
        return null;
    }
    
    
    public boolean canBeLabel(String token)
    {
        if (Tokenizer.isSymbol(token)) return true;
        if (allowNumberLabels && Tokenizer.isInteger(token)) return true;
        return false;
    }


    public boolean parseLabel(List<String> tokens, SourceLine sl, SourceStatement s, SourceFile source, CodeBase code, boolean defineInCodeBase) {
        if (tokens.isEmpty()) return true;

        String token = tokens.get(0);

        if (tokens.size() >= 2
                && canBeLabel(token)
                && tokens.get(1).equals(":")) {
            Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config);

            if (tokens.size() >= 3) {
                tokens.remove(0);
                tokens.remove(0);

                String symbolName = newSymbolName(labelPrefix + token, exp);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl.fileNameLineString() + ": " + sl.line);
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
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl.fileNameLineString() + ": " + sl.line);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) return false;
                }
                return parseRestofTheLine(tokens, sl, s, source);
            }
        } else if (canBeLabel(token)) {
            if (sl.line.startsWith(token) && (tokens.size() == 1 || Tokenizer.isSingleLineComment(tokens.get(1))) && 
                !config.preProcessor.isMacroIncludingEnds(token)) {
                // it is just a label without colon:
                if (config.warningLabelWithoutColon) {
                    config.warn("Style suggestion", s.fileNameLineString(),
                            "Label "+token+" defined without a colon.");
                }
                Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config);
                int address = exp.evaluate(s, code, false);
                tokens.remove(0);

                String symbolName = newSymbolName(labelPrefix + token, exp);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl.fileNameLineString() + ": " + sl.line);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, address, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) return false;
                }
                return parseRestofTheLine(tokens, sl, s, source);
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
                        config.warn("Style suggestion", s.fileNameLineString(),
                                "Label "+token+" defined without a colon.");
                    }
                    tokens.remove(0);
                    String symbolName = newSymbolName(labelPrefix + token, null);
                    if (symbolName == null) {
                        config.error("Problem defining symbol " + labelPrefix + token + " in " + sl.fileNameLineString() + ": " + sl.line);
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
            SourceLine sl,
            SourceStatement s, SourceFile source) {
        if (tokens.isEmpty()) {
            return true;
        }
        if (tokens.size() == 1 && Tokenizer.isSingleLineComment(tokens.get(0))) {
            s.comment = tokens.get(0);
            return true;
        }

        config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
        return false;
    }


    public boolean parseOrg(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {

        Expression exp = config.expressionParser.parse(tokens, s, code);
        if (exp == null) {
            config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }

        // skip optional second argument of .org (last memory address, tniASM syntax)
        if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) tokens.remove(0);
            if (tokens.isEmpty() || Tokenizer.isSingleLineComment(tokens.get(0))) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                return false;
            }
            // (for the moment, just ignore the second argument)
            // TODO This should validate that the second argument is an expression (and potentially evaluate it)
            tokens.clear();
        }

        s.type = SourceStatement.STATEMENT_ORG;
        s.org = exp;
        return parseRestofTheLine(tokens, sl, s, source);
    }


    public boolean parseInclude(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        if (tokens.size() >= 1) {
            String rawFileName = null;
            String token = tokens.get(0);
            if (Tokenizer.isString(token)) {
                tokens.remove(0);
                rawFileName = Tokenizer.stringValue(token);
            } else {
                if (allowIncludesWithoutQuotes) {
                    rawFileName = "";
                    while(!tokens.isEmpty()) {
                        if (Tokenizer.isSingleLineComment(tokens.get(0)) || 
                            Tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                        rawFileName += tokens.remove(0);
                    }
                }
            }
            if (rawFileName != null) {
                // recursive include file:
                String path = resolveIncludePath(rawFileName, source);
                SourceFile includedSource = codeBaseParser.parseSourceFile(path, code, source, s);
                if (includedSource == null) {
                    config.error("Problem including file at " + sl.fileNameLineString() + ": " + sl.line);
                    return false;
                } else {
                    s.type = SourceStatement.STATEMENT_INCLUDE;
                    s.include = includedSource;
                    return parseRestofTheLine(tokens, sl, s, source);
                }
            } 
        }
        config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
        return false;
    }


    public boolean parseIncbin(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        if (tokens.isEmpty()) {
            config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }
        String rawFileName = null;
        String token = tokens.get(0);        
        if (Tokenizer.isString(token)) {
                tokens.remove(0);
            rawFileName = Tokenizer.stringValue(token);
        } else {
            if (allowIncludesWithoutQuotes) {
                rawFileName = "";
                while(!tokens.isEmpty()) {
                    if (Tokenizer.isSingleLineComment(tokens.get(0)) || 
                        Tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                    rawFileName += tokens.remove(0);
                }
            }
        }
        if (rawFileName == null) {
            config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
            return false;        
        }
        String path = resolveIncludePath(rawFileName, source);
        if (path == null) {
            config.error("Incbin file " + rawFileName + " does not exist in " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }
        s.type = SourceStatement.STATEMENT_INCBIN;
        s.incbin = path;
        s.incbinOriginalStr = rawFileName;
        File f = new File(path);
        if (!f.exists()) {
            config.error("Incbin file " + rawFileName + " does not exist in " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }

        // optional skip and size arguments (they could be separated by commas or not, depending on the assembler dialect):
        Expression skip_exp = null;
        Expression size_exp = null;
        if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) tokens.remove(0);
            if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
                skip_exp = config.expressionParser.parse(tokens, s, code);
                if (skip_exp == null) {
                    config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                    return false;
                }
            }
        }
        if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) tokens.remove(0);
            if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
                size_exp = config.expressionParser.parse(tokens, s, code);
                if (skip_exp == null) {
                    config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                    return false;
                }
            }
        }

        s.incbinSkip = skip_exp;
        if (size_exp != null) {
            s.incbinSize = size_exp;
            s.incbinSizeSpecified = true;
        } else {
            s.incbinSize = Expression.constantExpression((int)f.length(), config);
            s.incbinSizeSpecified = false;
        }
        return parseRestofTheLine(tokens, sl, s, source);        
    }


    public boolean parseEqu(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code, boolean defineInCodeBase) {
        if (s.label == null) {
            config.error("Equ without label in line " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }
        Expression exp = config.expressionParser.parse(tokens, s, code);
        if (exp == null) {
            config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }
        s.type = SourceStatement.STATEMENT_CONSTANT;
        s.label.exp = exp;
        return parseRestofTheLine(tokens, sl, s, source);
    }


    public boolean parseData(List<String> tokens, String label,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<Expression> data = new ArrayList<>();
        boolean done = false;
        if (allowEmptyDB_DW_DD_definitions) {
            if (tokens.isEmpty() || Tokenizer.isSingleLineComment(tokens.get(0))) {
                data.add(Expression.constantExpression(0, config));
                done = true;
            }
        }
        while (!done) {
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
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

        return parseRestofTheLine(tokens, sl, s, source);
    }


    public boolean parseDefineSpace(List<String> tokens,
            SourceLine sl,
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
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                return false;
            }
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp;
            s.space_value = null;
        } else {
            // In this case, "ds" is just a short-hand for "db" with repeated values:
            Expression exp_amount = config.expressionParser.parse(tokens, s, code);
            Expression exp_value;
            if (exp_amount == null) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                return false;
            }
            if (!tokens.isEmpty() && tokens.get(0).startsWith(",")) {
                tokens.remove(0);
                exp_value = config.expressionParser.parse(tokens, s, code);
                if (exp_value == null) {
                    config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                    return false;
                }
            } else {
                exp_value = Expression.constantExpression(0, config);
            }

            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = exp_amount;
            s.space_value = exp_value;
        }

        return parseRestofTheLine(tokens, sl, s, source);
    }


    public boolean parseCPUOp(List<String> tokens, String opName,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<Expression> arguments = new ArrayList<>();
        while (!tokens.isEmpty()) {
            if (Tokenizer.isSingleLineComment(tokens.get(0))) {
                break;
            }
            Expression exp = null;
            if (allowNumberLabels) {
                // Check for some strange sjasm syntax concerning reusable labels:
                if (opName.equalsIgnoreCase("call") ||
                    opName.equalsIgnoreCase("jp") ||
                    opName.equalsIgnoreCase("jr")) {
                    String token = tokens.get(0);
                    if ((token.endsWith("f") || token.endsWith("F") || token.endsWith("b") || token.endsWith("B")) && 
                        Tokenizer.isInteger(token.substring(0, token.length()-1))) {
                        token = config.dialectParser.symbolName(token);
                        exp = Expression.symbolExpression(token, code, config);
                        tokens.remove(0);
                    }
                }
            }
            if (exp == null) exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
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
            config.error("No op spec matches with operator in line " + sl.fileNameLineString() + ": " + sl.line);
            return false;
        }

        s.type = SourceStatement.STATEMENT_CPUOP;
        s.op = op;
        return parseRestofTheLine(tokens, sl, s, source);
    }


    public boolean parseMacroDefinition(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        // Marks that all the lines that come after this, and until ENDM,
        // are part of a macro, and should not yet be parsed:
        if (macroNameIsFirstArgumentOfMacro) {
            if (s.label != null || tokens.isEmpty()) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                return false;
            }
            String macroNameStr = tokens.remove(0);
            SourceConstant c = new SourceConstant(macroNameStr, null, null, s);
            s.label = c;
        } else {
            if (s.label == null) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
                return false;
            }
        }

        // parseArgs arguments:
        List<String> args = new ArrayList<>();
        List<Expression> defaultValues = new ArrayList<>();
        while (!tokens.isEmpty() && 
               !Tokenizer.isSingleLineComment(tokens.get(0)) &&
               !Tokenizer.isMultiLineCommentStart(tokens.get(0))) {
            String token = tokens.get(0);
            tokens.remove(0);
            if (token.equals("?")) {
                args.add(token + tokens.remove(0));
            } else {
                args.add(token);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals("=")) {
                // default value:
                tokens.remove(0);
                Expression defaultValue = config.expressionParser.parse(tokens, s, code);
                if (defaultValue == null) {
                    config.error("Cannot parse default value in line " + sl.fileNameLineString() + ": " + sl.line);
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
        return parseRestofTheLine(tokens, sl, s, source);
    }


    public boolean parseMacroCall(List<String> tokens, String macroName,
            SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code) {
        List<Expression> arguments = new ArrayList<>();
        while (!tokens.isEmpty()) {
            if (Tokenizer.isSingleLineComment(tokens.get(0))) {
                break;
            }
            Expression exp = config.expressionParser.parse(tokens, s, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl.fileNameLineString() + ": " + sl.line);
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
        return parseRestofTheLine(tokens, sl, s, source);
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


    public String pathConcat(String path, String fileName)
    {
        if (path.endsWith(File.separator)) {
            return path + fileName;
        } else {
            return path + File.separator + fileName;
        }
    }


}

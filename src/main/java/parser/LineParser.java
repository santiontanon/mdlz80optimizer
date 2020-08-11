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
    public String KEYWORD_COLON = ":";
    HashMap<String, String> keywordSynonyms = new HashMap<>();
    public List<String> keywordsHintingALabel = new ArrayList<>();

    // If this is set to true then "ds 1" is the same as "ds virtual 1"
    public boolean defineSpaceVirtualByDefault = false;
    public boolean allowEmptyDB_DW_DD_definitions = false;
    public boolean allowIncludesWithoutQuotes = false;
    public boolean allowExtendedSjasmInstructions = false;

    // sjasm defines macros like: "macro macroname arg1,...,agn" instead of "macroname: macro arg1,...,argn":
    public boolean macroNameIsFirstArgumentOfMacro = false;
    public boolean allowNumberLabels = false;   // also for sjasm (for "reusable" labels)

    public boolean sdccStyleOffsets = false;
    
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

    public boolean isKeyword(String token) {
        if (isKeyword(token, KEYWORD_ORG) ||
            isKeyword(token, KEYWORD_INCLUDE) ||
            isKeyword(token, KEYWORD_INCBIN) ||
            isKeyword(token, KEYWORD_EQU) ||
            isKeyword(token, KEYWORD_DB) ||
            isKeyword(token, KEYWORD_DW) ||
            isKeyword(token, KEYWORD_DD) ||
            isKeyword(token, KEYWORD_DS)) {
            return true;
        }
        return false;
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
    
    
    public void clearPrefixStack() {
        labelPrefixStack.clear();
        labelPrefix = "";
    }
    

    public String getLabelPrefix() {
        return labelPrefix;
    }

    public String newSymbolName(String rawName, Expression value, SourceStatement previous) {
        String name = rawName;
        if (config.dialectParser != null) {
            name = config.dialectParser.newSymbolName(name, value, previous);
        }

        if (allowNumberLabels && Tokenizer.isInteger(name)) {
            return name;
        } else {
            name = labelPrefix + name;
        }
        
        return name;
    }

    // insertionPoint is used to determine the label scope:
    public List<SourceStatement> parse(List<String> tokens, SourceLine sl,
            SourceFile f, int insertionPoint, CodeBase code, MDLConfig config) {
        SourceStatement previous = null;
        if (insertionPoint >= 0) {
            if (f.getStatements().size()>insertionPoint) {
                previous = f.getPreviousStatementTo(f.getStatements().get(insertionPoint), code);
            } else {
                if (!f.getStatements().isEmpty()) previous = f.getStatements().get(f.getStatements().size()-1);
            }
        }
        return parse(tokens, sl, f, previous, code, config);
    }

    
     public List<SourceStatement> parse(List<String> tokens, SourceLine sl,
            SourceFile f, SourceStatement previous, CodeBase code, MDLConfig config) {
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, sl, f, config);
        if (sl.labelPrefixToPush != null) pushLabelPrefix(sl.labelPrefixToPush);
        if (sl.labelPrefixToPop != null) popLabelPrefix();
        if (labelPrefix != null) s.labelPrefix = labelPrefix;
        List<SourceStatement> l = parseInternal(tokens, sl, s, previous, f, code);
        return l;
    }

    
    List<SourceStatement> parseInternal(List<String> tokens, SourceLine sl, SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        if (!parseLabel(tokens, sl, s, previous, source, code, true)) {
            return null;
        }
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);

        if (tokens.isEmpty()) {
            return l;
        }
        String token = tokens.get(0);

        if (isKeyword(token, KEYWORD_ORG)) {
            tokens.remove(0);
            if (parseOrg(tokens, sl, s, previous, source, code)) {
                return l;
            }
        } else if (isKeyword(token, KEYWORD_INCLUDE)) {
            tokens.remove(0);
            if (parseInclude(tokens, sl, s, source, code)) {
                return l;
            }

        } else if (isKeyword(token, KEYWORD_INCBIN)) {
            tokens.remove(0);
            if (parseIncbin(tokens, sl, s, previous, source, code)) {
                return l;
            }
        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_EQU)) {
            tokens.remove(0);
            if (parseEqu(tokens, sl, s, previous, source, code)) {
                return l;
            }
        } else if (tokens.size() >= 1
                && (isKeyword(token, KEYWORD_DB)
                || isKeyword(token, KEYWORD_DW)
                || isKeyword(token, KEYWORD_DD))) {
            tokens.remove(0);
            if (parseData(tokens, token, sl, s, previous, source, code)) {
                return l;
            }

        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_DS)) {
            tokens.remove(0);
            if (parseDefineSpace(tokens, sl, s, previous, source, code)) {
                return l;
            }

        } else if (isKeyword(token, config.preProcessor.MACRO_MACRO)) {
            tokens.remove(0);
            if (parseMacroDefinition(tokens, sl, s, previous, source, code)) {
                return l;
            }

        } else if (isKeyword(token, config.preProcessor.MACRO_ENDM)) {
            config.error(config.preProcessor.MACRO_ENDM + " keyword found outside of a macro at " + source.fileName + ", "
                    + sl.fileNameLineString());
            return null;

        } else if (config.dialectParser != null && config.dialectParser.recognizeIdiom(tokens)) {
            // this one might return one or more statements:
            return config.dialectParser.parseLine(tokens, sl, s, previous, source, code);
        } else if (Tokenizer.isSymbol(token)) {
            // try to parseArgs it as an assembler instruction or macro call:
            tokens.remove(0);
            if (config.opParser.isOpName(token)) {
                if (parseCPUOp(tokens, token, sl, l, previous, source, code)) {
                    return l;
                }
            } else {
                if (parseMacroCall(tokens, token, sl, s, previous, source, code)) {
                    return l;
                }
            }
        } else {
            if (parseRestofTheLine(tokens, sl, s, source)) {
                return l;
            }
        }
        return null;
    }

    public boolean canBeLabel(String token) {
        if (isKeyword(token)) {
            return false;
        }
        if (Tokenizer.isSymbol(token)) {
            return true;
        }
        if (allowNumberLabels && Tokenizer.isInteger(token)) {
            return true;
        }
        return false;
    }

    public boolean parseLabel(List<String> tokens, SourceLine sl, SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code, boolean defineInCodeBase) {
        if (tokens.isEmpty()) {
            return true;
        }

        String token = tokens.get(0);

        if (tokens.size() >= 2
                && canBeLabel(token)
                && isKeyword(tokens.get(1),KEYWORD_COLON)) {
            Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);

            if (tokens.size() >= 3) {
                tokens.remove(0);
                tokens.remove(0);

                String symbolName = newSymbolName(token, exp, previous);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, token, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) {
                        return false;
                    }
                }
            } else {
                tokens.remove(0);
                tokens.remove(0);

                String symbolName = newSymbolName(token, exp, previous);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, token, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) {
                        return false;
                    }
                }
                return parseRestofTheLine(tokens, sl, s, source);
            }
        } else if (canBeLabel(token) && !config.preProcessor.isMacroIncludingEnds(token)) {
            if (sl.line.startsWith(token) && (tokens.size() == 1 || Tokenizer.isSingleLineComment(tokens.get(1)))) {
                if (!config.opParser.getOpSpecs(tokens.get(0)).isEmpty()) return true;
                
                // it is just a label without colon:
                if (config.warningLabelWithoutColon) {
                    config.warn("Style suggestion", s.fileNameLineString(),
                            "Label " + token + " defined without a colon.");
                }
                Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);
                tokens.remove(0);

                String symbolName = newSymbolName(token, exp, previous);
                if (symbolName == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                    return false;
                }
                SourceConstant c = new SourceConstant(symbolName, token, null, exp, s);
                s.type = SourceStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    if (!code.addSymbol(c.name, c)) {
                        return false;
                    }
                }
                return parseRestofTheLine(tokens, sl, s, source);
            } else if (tokens.size() >= 2) {
                boolean isLabel = false;
                if (sl.line.startsWith(token)) isLabel = true;
                if (config.dialectParser != null && isLabel) {
                    if (config.dialectParser.recognizeIdiom(tokens)) isLabel = false;
                }
                if (isLabel) {
                    if (!config.opParser.getOpSpecs(tokens.get(0)).isEmpty()) isLabel = false;
                }
                for (String keyword : keywordsHintingALabel) {
                    if (isKeyword(tokens.get(1), keyword)) {
                        isLabel = true;
                        break;
                    }
                }
                if (isLabel) {
                    if (config.warningLabelWithoutColon
                            && !tokens.get(1).equals(":=") && !tokens.get(1).equals("=")) {
                        config.warn("Style suggestion", s.fileNameLineString(),
                                "Label " + token + " defined without a colon.");
                    }
                    tokens.remove(0);
                    String symbolName = newSymbolName(token, null, previous);
                    if (symbolName == null) {
                        config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                        return false;
                    }
                    SourceConstant c = new SourceConstant(symbolName, token, null, null, s);
                    s.type = SourceStatement.STATEMENT_NONE;
                    s.label = c;
                    if (defineInCodeBase) {
                        if (!code.addSymbol(c.name, c)) {
                            return false;
                        }
                    }
                    // If it did not have a previous value, we assign one:
                    if (c.exp == null) {
                        c.exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);
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

        config.error("parseRestofTheLine: Cannot parse line " + sl);
        return false;
    }

    public boolean parseOrg(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {

        Expression exp = config.expressionParser.parse(tokens, s, previous, code);
        if (exp == null) {
            config.error("parseOrg: Cannot parse line " + sl);
            return false;
        }

        // skip optional second argument of .org (last memory address, tniASM syntax)
        if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) {
                tokens.remove(0);
            }
            if (tokens.isEmpty() || Tokenizer.isSingleLineComment(tokens.get(0))) {
                config.error("parseOrg: Cannot parse line " + sl);
                return false;
            }
            // (for the moment, just ignore the second argument)
            // TODO This should validate that the second argument is an expression (and potentially evaluateToInteger it)
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
                    while (!tokens.isEmpty()) {
                        if (Tokenizer.isSingleLineComment(tokens.get(0))
                                || Tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                            break;
                        }
                        rawFileName += tokens.remove(0);
                    }
                }
            }
            if (rawFileName != null) {
                // recursive include file:
                String path = resolveIncludePath(rawFileName, source);
                SourceFile includedSource = codeBaseParser.parseSourceFile(path, code, source, s);
                if (includedSource == null) {
                    config.error("Problem including file at " + sl);
                    return false;
                } else {
                    s.type = SourceStatement.STATEMENT_INCLUDE;
                    s.include = includedSource;
                    return parseRestofTheLine(tokens, sl, s, source);
                }
            }
        }
        config.error("parseInclude: Cannot parse line " + sl);
        return false;
    }

    public boolean parseIncbin(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        if (tokens.isEmpty()) {
            config.error("parseIncbin: Cannot parse line " + sl);
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
                while (!tokens.isEmpty()) {
                    if (tokens.get(0).equals(",")
                            || Tokenizer.isSingleLineComment(tokens.get(0))
                            || Tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                        break;
                    }
                    rawFileName += tokens.remove(0);
                }
            }
        }
        if (rawFileName == null) {
            config.error("parseIncbin: Cannot parse line " + sl);
            return false;
        }
        String path = resolveIncludePath(rawFileName, source);
        if (path == null) {
            config.error("Incbin file " + rawFileName + " does not exist in " + sl);
            return false;
        }
        s.type = SourceStatement.STATEMENT_INCBIN;
        s.incbin = new File(path);
        s.incbinOriginalStr = rawFileName;
        File f = new File(path);
        if (!f.exists()) {
            config.error("Incbin file " + rawFileName + " does not exist in " + sl);
            return false;
        }

        // optional skip and size arguments (they could be separated by commas or not, depending on the assembler dialect):
        Expression skip_exp = null;
        Expression size_exp = null;
        if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) {
                tokens.remove(0);
            }
            if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
                skip_exp = config.expressionParser.parse(tokens, s, previous, code);
                if (skip_exp == null) {
                    config.error("parseIncbin: Cannot parse line " + sl);
                    return false;
                }
            }
        }
        if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) {
                tokens.remove(0);
            }
            if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {
                size_exp = config.expressionParser.parse(tokens, s, previous, code);
                if (skip_exp == null) {
                    config.error("parseIncbin: Cannot parse line " + sl);
                    return false;
                }
            }
        }

        s.incbinSkip = skip_exp;
        if (size_exp != null) {
            s.incbinSize = size_exp;
            s.incbinSizeSpecified = true;
        } else {
            if (skip_exp != null) {
                s.incbinSize = Expression.operatorExpression(
                        Expression.EXPRESSION_SUB,
                        Expression.constantExpression((int) f.length(), config),
                        skip_exp, config);
                s.incbinSizeSpecified = false;
            } else {
                s.incbinSize = Expression.constantExpression((int) f.length(), config);
                s.incbinSizeSpecified = false;
            }
        }
        return parseRestofTheLine(tokens, sl, s, source);
    }

    public boolean parseEqu(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        if (s.label == null) {
            config.error("Equ without label in line " + sl);
            return false;
        }
        Expression exp = config.expressionParser.parse(tokens, s, previous, code);
        if (exp == null) {
            config.error("parseEqu: Cannot parse line " + sl);
            return false;
        }

        // remove unnecessary parenthesis:
        while(exp.type == Expression.EXPRESSION_PARENTHESIS) {
            exp = exp.args.get(0);
        }
        
        s.type = SourceStatement.STATEMENT_CONSTANT;
        s.label.exp = exp;
        return parseRestofTheLine(tokens, sl, s, source);
    }

    public boolean parseData(List<String> tokens, String label,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        List<Expression> data = new ArrayList<>();
        boolean done = false;
        if (allowEmptyDB_DW_DD_definitions) {
            if (tokens.isEmpty() || Tokenizer.isSingleLineComment(tokens.get(0))) {
                data.add(Expression.constantExpression(0, config));
                done = true;
            }
        }
        while (!done) {
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("parseData: Cannot parse line " + sl);
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
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        boolean virtual = false;
        if (tokens.get(0).equalsIgnoreCase("virtual")) {
            tokens.remove(0);
            virtual = true;
        }
        if (defineSpaceVirtualByDefault) {
            virtual = true;
        }
//        if (virtual) {
//            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
//            if (exp == null) {
//                config.error("parseDefineSpace: Cannot parse line " + sl);
//                return false;
//            }
//            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
//            s.space = exp;
//            s.space_value = null;
//        } else {
        // In this case, "ds" is just a short-hand for "db" with repeated values:
        Expression exp_amount = config.expressionParser.parse(tokens, s, previous, code);
        Expression exp_value = null;
        if (exp_amount == null) {
            config.error("parseDefineSpace: Cannot parse line " + sl);
            return false;
        }
        if (!tokens.isEmpty() && tokens.get(0).startsWith(",")) {
            tokens.remove(0);
            exp_value = config.expressionParser.parse(tokens, s, previous, code);
            if (exp_value == null) {
                config.error("parseDefineSpace: Cannot parse line " + sl);
                return false;
            }
        } else {
            if (!virtual) exp_value = Expression.constantExpression(0, config);
        }

        s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
        s.space = exp_amount;
        s.space_value = exp_value;
//        }

        return parseRestofTheLine(tokens, sl, s, source);
    }

    public boolean parseCPUOp(List<String> tokens, String opName, SourceLine sl,
            List<SourceStatement> l, SourceStatement previous, SourceFile source, CodeBase code) {
        SourceStatement s = l.get(0);
        
        tokens.add(0, opName);
        if (config.dialectParser != null) {
            // put the opName back into the tokens (in case the tokens are modified by the fake CPU op parsing:
            if (!config.dialectParser.parseFakeCPUOps(tokens, sl, l, previous, source, code)) return false;
        }
        
        opName = tokens.remove(0);
        List<Expression> arguments = new ArrayList<>();

        while (!tokens.isEmpty()) {
            if (Tokenizer.isSingleLineComment(tokens.get(0))) {
                break;
            }
            Expression exp = null;
            if (allowNumberLabels) {
                // Check for some strange sjasm syntax concerning reusable labels:
                if (opName.equalsIgnoreCase("call")
                        || opName.equalsIgnoreCase("jp")
                        || opName.equalsIgnoreCase("jr")
                        || opName.equalsIgnoreCase("djnz")) {
                    String token = tokens.get(0);
                    if ((token.endsWith("f") || token.endsWith("F") || token.endsWith("b") || token.endsWith("B"))
                            && Tokenizer.isInteger(token.substring(0, token.length() - 1))) {
                        token = config.dialectParser.symbolName(token, s);
                        exp = Expression.symbolExpression(token, s, code, config);
                        tokens.remove(0);
                    }
                }
            }
            if (exp == null) {
                exp = config.expressionParser.parse(tokens, s, previous, code);
            }
            if (exp == null) {
                config.error("parseCPUOp: Cannot parse line " + sl);
                return false;
            }
            
            if (sdccStyleOffsets && !tokens.isEmpty()) {
                if (tokens.get(0).equals("(")) {
                    // offset of the type: "offset (register)", meaning (register+offset):
                    Expression exp2 = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp2 == null || exp2.type != Expression.EXPRESSION_PARENTHESIS ||
                        exp2.args.size()!=1 || 
                        exp2.args.get(0).type != Expression.EXPRESSION_REGISTER_OR_FLAG) {
                        config.error("parseCPUOp: Cannot parse line " + sl);
                        return false;                        
                    }
                    Expression exp_tmp = Expression.operatorExpression(Expression.EXPRESSION_SUM, exp2.args.get(0), exp, config);
                    exp2.args.set(0, exp_tmp);
                    exp = exp2;
                }
            }
            
            arguments.add(exp);
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }

        List<CPUOp> op_l = config.opParser.parseOp(opName, arguments, s, previous, code);
        if (op_l == null) {
            config.error("No op spec matches with operator in line " + sl);
            return false;
        }

        
        s.type = SourceStatement.STATEMENT_CPUOP;
        s.op = op_l.get(0);
        
        for(int i = 1;i<op_l.size();i++) {
            SourceStatement s2 = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
            s2.type = SourceStatement.STATEMENT_CPUOP;
            s2.op = op_l.get(i);
            l.add(s2);
        }

        return parseRestofTheLine(tokens, sl, s, source);
    }
    

    public boolean parseMacroDefinition(List<String> tokens,
            SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        // Marks that all the lines that come after this, and until ENDM,
        // are part of a macro, and should not yet be parsed:
        if (macroNameIsFirstArgumentOfMacro) {
            if (s.label != null || tokens.isEmpty()) {
                config.error("parseMacroDefinition: Cannot parse line " + sl);
                return false;
            }
            String macroNameStr = tokens.remove(0);
            SourceConstant c = new SourceConstant(macroNameStr, macroNameStr, null, null, s);
            s.label = c;
        } else {
            if (s.label == null) {
                config.error("parseMacroDefinition: Cannot parse line " + sl);
                return false;
            }
        }

        // parseArgs arguments:
        List<String> args = new ArrayList<>();
        List<Expression> defaultValues = new ArrayList<>();
        while (!tokens.isEmpty()
                && !Tokenizer.isSingleLineComment(tokens.get(0))
                && !Tokenizer.isMultiLineCommentStart(tokens.get(0))) {
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
                Expression defaultValue = config.expressionParser.parse(tokens, s, previous, code);
                if (defaultValue == null) {
                    config.error("Cannot parse default value in line " + sl);
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
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        List<Expression> arguments = new ArrayList<>();
        // special case for "IFDEF", which takes a special kind of argument (which should never be
        // evaluated, regardless if it's an eager variable or not):
        boolean isIfDef = false;
        if (config.preProcessor.isMacroName(macroName, config.preProcessor.MACRO_IFDEF) ||
            config.preProcessor.isMacroName(macroName, config.preProcessor.MACRO_IFNDEF)) {
            isIfDef = true;
        }
        while (!tokens.isEmpty()) {
            if (Tokenizer.isSingleLineComment(tokens.get(0))) {
                break;
            }
            Expression exp;
            if (isIfDef) {
                String token = tokens.remove(0);
                if (config.dialectParser != null) token = config.dialectParser.symbolName(token, previous);
                exp = Expression.symbolExpressionInternal(token, s, code, false, config);
            } else {
                exp = config.expressionParser.parse(tokens, s, previous, code);
            }
            if (exp == null) {
                config.error("parseMacroCall: Cannot parse line " + sl);
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

        // Make sure we don't have a windows/Unix path separator problem:
        if (rawFileName.contains("\\")) {
            rawFileName = rawFileName.replace("\\", File.separator);
        }

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

    public String pathConcat(String path, String fileName) {
        if (path.endsWith(File.separator) || path.isEmpty()) {
            return path + fileName;
        } else {
            return path + File.separator + fileName;
        }
    }

}

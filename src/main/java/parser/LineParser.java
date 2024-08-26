/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.util.Arrays;
import org.apache.commons.lang3.tuple.Pair;
import util.Resources;

public class LineParser {
    public static final int MACRO_LABEL_MACRO_ARGS = 1;
    public static final int MACRO_MACRO_NAME_ARGS = 2;
    public static final int MACRO_BOTH = 3;
    
    // Standard versions, to be used for generating standard assembler:
    public final String KEYWORD_STD_ORG = "org";
    public final String KEYWORD_STD_INCLUDE = "include";
    public final String KEYWORD_STD_INCBIN = "incbin";
    public final String KEYWORD_STD_EQU = "equ";
    public final String KEYWORD_STD_DB = "db";
    public final String KEYWORD_STD_DW = "dw";
    public final String KEYWORD_STD_DD = "dd";
    public final String KEYWORD_STD_DS = "ds";
    public final String KEYWORD_STD_COLON = ":";
    public final String KEYWORD_STD_FPOS = "fpos";

    // Dialect specific versions that will be used for parsing, or for generatinc dialect assembler:
    public String KEYWORD_ORG = "org";
    public String KEYWORD_INCLUDE = "include";
    public String KEYWORD_INCBIN = "incbin";
    public String KEYWORD_EQU = "equ";
    public String KEYWORD_DB = "db";
    public String KEYWORD_DW = "dw";
    public String KEYWORD_DD = "dd";
    public String KEYWORD_DS = "ds";
    public String KEYWORD_COLON = ":";
    public String KEYWORD_FPOS = "fpos";
    HashMap<String, String> keywordSynonyms = new HashMap<>();
    public List<String> keywordsHintingALabel = new ArrayList<>();
    public List<String> keywordsHintingANonScopedLabel = new ArrayList<>();
    public List<String> macroArguentPrefixes = new ArrayList<>();
    
    // If a label is defined with any of these names, it will be renamed to "__mdlrenamed__"+symbol:
    public List<String> forbiddenSymbols = new ArrayList<>();

    // If this is set to true then "ds 1" is the same as "ds virtual 1"
    public boolean defineSpaceVirtualByDefault = false;
    public boolean allowEmptyDB_DW_DD_definitions = false;
    public boolean emptyDB_DW_DD_definitions_define_only_space = false;
    public boolean allowIncludesWithoutQuotes = false;

    // sjasm defines macros like: "macro macroname arg1,...,agn" instead of "macroname: macro arg1,...,argn":
    public int macroDefinitionStyle = MACRO_LABEL_MACRO_ARGS;
    public boolean tniAsmStylemacroArgs = false;
    public boolean tniAsm045MultipleInstructionsPerLine = false;
    public boolean tniAsm10MultipleInstructionsPerLine = false;
    // Pasmo allows you to call macros with less parameters than defined, just using empty strings as defaults:
    public boolean emptyStringDefaultArgumentsForMacros = false;
    public boolean allowNumberLabels = false;   // also for sjasm (for "reusable" labels)
    public boolean applyEscapeSequencesToIncludeArguments = true;
    public String macroKeywordPrecedingArguments = null;

    public boolean sdccStyleOffsets = false;
    
    public boolean allowColonSeparatedInstructions = false;
    public boolean allowBackslashAsLineBreaks = false;
    public boolean allowdataLinesWithoutCommas = false;
    
    public String reptIndexArgSeparator = null;
    
    public List<String> tokensPreventingTextMacroExpansion = new ArrayList<>();
    
    MDLConfig config;
    CodeBaseParser codeBaseParser;

    // for local labels:
    String labelPrefix = "";
    List<String> labelPrefixStack = new ArrayList<>();
    
    boolean insideMultiLineComment = false;

    public LineParser(MDLConfig a_config, CodeBaseParser a_codeBaseParser) {
        config = a_config;
        codeBaseParser = a_codeBaseParser;

        resetKeywordsHintingALabel();
                
        forbiddenSymbols.add("end");
        
        macroArguentPrefixes.add("?");
    }
    
    
    public final void resetKeywordsHintingALabel()
    {
        keywordsHintingALabel.clear();
        keywordsHintingALabel.add(KEYWORD_EQU);
        keywordsHintingALabel.add(KEYWORD_DB);
        keywordsHintingALabel.add(KEYWORD_DW);
        keywordsHintingALabel.add(KEYWORD_DD);        

        keywordsHintingANonScopedLabel.clear();
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
            isKeyword(token, KEYWORD_DS) ||
            isKeyword(token, KEYWORD_FPOS)) {
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
    
    
    public String newSymbolNameNotLabel(String rawName, CodeStatement previous) {
        String name = rawName;
        
        for(String forbiddenSymbol: forbiddenSymbols) {
            if (forbiddenSymbol.equalsIgnoreCase(name)) {
                name = "__mdlrenamed__" + name;
                break;
            }
        }
        
        if (config.dialectParser != null) {
            Pair<String, SourceConstant> tmp = config.dialectParser.symbolName(name, previous);
            if (tmp == null) return null;
            name = tmp.getLeft();
        }
        
        return name;
    }    
    
    
    public SourceConstant newSourceConstant(String rawName, Expression value, CodeStatement s, CodeStatement previous) {
        String name = rawName;
        SourceConstant relativeTo = null;
        
        for(String forbiddenSymbol: forbiddenSymbols) {
            if (forbiddenSymbol.equalsIgnoreCase(name)) {
                name = "__mdlrenamed__" + name;
                break;
            }
        }
        
        if (config.dialectParser != null) {
            Pair<String, SourceConstant> tmp = config.dialectParser.newSymbolName(name, value, previous);
            if (tmp == null) return null;
            name = tmp.getLeft();
            relativeTo = tmp.getRight();
        } else {
            if ((allowNumberLabels && config.tokenizer.isInteger(name)) ||
                (config.tokenizer.allowDashPlusLabels && config.tokenizer.isDashPlusLabel(name))) {
            } else {
                // if (!allowNumberLabels || !config.tokenizer.isInteger(name)) {
                name = labelPrefix + name;
            }
        }

        SourceConstant c = new SourceConstant(name, rawName, value, s, config);
        c.relativeTo = relativeTo;
        return c;
    }    

    // insertionPoint is used to determine the label scope:
    public List<CodeStatement> parse(List<String> tokens, SourceLine sl,
            SourceFile f, int insertionPoint, CodeBase code, MDLConfig config) {
        CodeStatement previous = null;
        if (insertionPoint >= 0) {
            if (f.getStatements().size()>insertionPoint) {
                previous = f.getPreviousStatementTo(f.getStatements().get(insertionPoint), code);
            } else {
                if (!f.getStatements().isEmpty()) previous = f.getStatements().get(f.getStatements().size()-1);
            }
        }
        return parse(tokens, sl, f, previous, code, config);
    }

    
     public List<CodeStatement> parse(List<String> tokens, SourceLine sl,
            SourceFile f, CodeStatement previous, CodeBase code, MDLConfig config) {
        CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_NONE, sl, f, config);
        if (sl.labelPrefixToPush != null) pushLabelPrefix(sl.labelPrefixToPush);
        if (sl.labelPrefixToPop != null) popLabelPrefix();
        if (labelPrefix != null) s.labelPrefix = labelPrefix;
        List<CodeStatement> l = parseInternal(tokens, sl, s, previous, f, code);
        return l;
    }

    
    List<CodeStatement> parseInternal(List<String> tokens, SourceLine sl, CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {        
        List<CodeStatement> l = new ArrayList<>();
        l.add(s);        
        if (parseInternal(tokens, l, sl, s, previous, source, code, true)) return l;
        return null;
    }

    
    boolean parseInternal(List<String> tokens, List<CodeStatement> l, SourceLine sl, CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code, boolean allowLabel) 
    {    
        if (insideMultiLineComment) {
            // we are still inside a multiline comment
            return parseLineInsideMultilineComment(tokens, l, sl, s, previous, source, code);
        }
        
        // apply text macros:
        if (!tokens.isEmpty()) {
            if (!tokensPreventingTextMacroExpansion.contains(tokens.get(0).toLowerCase())) {
                config.preProcessor.expandTextMacros(tokens, s, sl);
            }
        }
        
        if (allowLabel) {
            if (!parseLabel(tokens, l, sl, s, previous, source, code, true)) {
                return false;
            }
        }
        
        if (tokens.isEmpty()) {
            return true;
        }
        String token = tokens.get(0);

        if (config.dialectParser != null && config.dialectParser.recognizeIdiom(tokens, s.label, code)) {
            // this one might return one or more statements:
            return config.dialectParser.parseLine(tokens, l, sl, s, previous, source, code);
        
        } else if (isKeyword(token, KEYWORD_ORG)) {
            tokens.remove(0);
            if (parseOrg(tokens, l, sl, s, previous, source, code)) {
                return true;
            }
        } else if (isKeyword(token, KEYWORD_INCLUDE)) {
            tokens.remove(0);
            if (parseInclude(tokens, l, sl, s, previous, source, code)) {
                return true;
            }

        } else if (isKeyword(token, KEYWORD_INCBIN)) {
            tokens.remove(0);
            if (parseIncbin(tokens, l, sl, s, previous, source, code)) {
                return true;
            }
        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_EQU)) {
            tokens.remove(0);
            if (parseEqu(tokens, l, sl, s, previous, source, code)) {
                return true;
            }
        } else if (tokens.size() >= 1
                && (isKeyword(token, KEYWORD_DB)
                || isKeyword(token, KEYWORD_DW)
                || isKeyword(token, KEYWORD_DD))) {
            tokens.remove(0);
            if (parseData(tokens, token, l, sl, s, previous, source, code)) {
                return true;
            }

        } else if (tokens.size() >= 2 && isKeyword(token, KEYWORD_DS)) {
            tokens.remove(0);
            if (parseDefineSpace(tokens, l, sl, s, previous, source, code)) {
                return true;
            }

        } else if (isKeyword(token, config.preProcessor.MACRO_MACRO)) {
            tokens.remove(0);
            if (parseMacroDefinition(tokens, l, sl, s, previous, source, code)) {
                return true;
            }

        } else if (isKeyword(token, config.preProcessor.MACRO_ENDM)) {
            config.error(config.preProcessor.MACRO_ENDM + " keyword found outside of a macro in " + source.fileName + ", "
                    + sl.fileNameLineString());
            return false;

        } else if (isKeyword(token, KEYWORD_FPOS)) {
            tokens.remove(0);
            if (parseFpos(tokens, l, sl, s, previous, source, code)) {
                return true;
            }
            
        } else if (config.tokenizer.isSymbol(token, config.allowNumberStartingSymbols)) {
            // try to parseArgs it as an assembler instruction or macro call:
            tokens.remove(0);
            if (config.opParser.isOpName(token)) {
                if (parseCPUOp(tokens, token, sl, l, previous, source, code)) {
                    return true;
                }
            } else {
                if (parseMacroCall(tokens, token, l, sl, s, previous, source, code)) {
                    return true;
                }
            }
        } else if (config.preProcessor.isMacro(token)) {
            tokens.remove(0);
            if (parseMacroCall(tokens, token, l, sl, s, previous, source, code)) {
                return true;
            }
        } else {
            if (parseRestofTheLineInternal(tokens, l, sl, s, previous, false, source, code)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean canBeLabel(String token, boolean indented) {
        if (isKeyword(token)) return false;
        if (config.opParser.isOpName(token, 0) && !config.considerCpuOpSymbolsWithoutIndentationToBeLabels) return false;
        if (config.preProcessor.isMacroName(token, config.preProcessor.MACRO_MACRO)) return false;
        if ((macroDefinitionStyle == MACRO_MACRO_NAME_ARGS || macroDefinitionStyle == MACRO_BOTH)
            && config.preProcessor.isMacro(token)) return false;
        if (config.tokenizer.isSymbol(token, config.allowNumberStartingSymbols)) return true;
        if (allowNumberLabels && config.tokenizer.isInteger(token)) return true;
        if (config.tokenizer.allowDashPlusLabels && config.tokenizer.isDashPlusLabel(token)) return true;
        if (config.allowNumberStartingSymbols && token.charAt(0) >= '0' && token.charAt(0) <= '9' && config.tokenizer.containsLetter(token)) return true;
        return false;
    }

    public boolean parseLabel(List<String> tokens, List<CodeStatement> l, SourceLine sl, CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code, boolean defineInCodeBase) {
        if (tokens.isEmpty()) {
            return true;
        }

        String token = tokens.get(0);
        boolean indented = !sl.line.startsWith(token);

        if (tokens.size() >= 2
                && canBeLabel(token, indented)
                && isKeyword(tokens.get(1),KEYWORD_COLON)) {
            Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);

            if (tokens.size() >= 3) {
                tokens.remove(0);
                String colonToken = tokens.remove(0);   // we remember the type of "colon" used,
                                                        // since in some dialects, it does matter

                if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                if (config.convertSymbolstoUpperCase) token = token.toUpperCase();
                SourceConstant c = newSourceConstant(token, exp, s, previous);
                if (c == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                    return false;
                }
                c.colonTokenUsedInDefinition = colonToken;
                s.type = CodeStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    int res = code.addSymbol(c.name, c);
                    if (res == -1) return false;
                    if (res == 0) s.redefinedLabel = true;
                }
            } else {
                tokens.remove(0);
                String colonToken = tokens.remove(0);   // we remember the type of "colon" used,
                                                        // since in some dialects, it does matter

                if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                if (config.convertSymbolstoUpperCase) token = token.toUpperCase();
                SourceConstant c = newSourceConstant(token, exp, s, previous);
                if (c == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                    return false;
                }
                c.colonTokenUsedInDefinition = colonToken;
                s.type = CodeStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    // in this case, symbol redefinition is always an error, as this is just a label!
                    int res = code.addSymbol(c.name, c);
                    if (res != 1) return false;
                }
                return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
            }
        } else if (canBeLabel(token, indented) && !config.preProcessor.isMacroIncludingEnds(token)) {
            if (!indented && (tokens.size() == 1 || config.tokenizer.isSingleLineComment(tokens.get(1)))) {
                if (!config.opParser.getOpSpecs(tokens.get(0)).isEmpty()) return true;
                
                if (config.dialectParser != null &&
                    config.dialectParser.recognizeIdiom(tokens, s.label, code)) return true;
                
                // it is just a label without colon:
                if (config.warning_labelWithoutColon) {
                    config.warn("Style suggestion", s.fileNameLineString(),
                            "Label " + token + " defined without a colon.");
                }
                Expression exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);
                tokens.remove(0);

                if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                if (config.convertSymbolstoUpperCase) token = token.toUpperCase();
                SourceConstant c = newSourceConstant(token, exp, s, previous);
                if (c == null) {
                    config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                    return false;
                }
                s.type = CodeStatement.STATEMENT_NONE;
                s.label = c;
                if (defineInCodeBase) {
                    // in this case, symbol redefinition is always an error, as this is just a label!
                    int res = code.addSymbol(c.name, c);
                    if (res != 1) return false;
                }
                return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
            } else if (tokens.size() >= 2) {
                boolean isLabel = false;
                boolean scope = true;
                if (!indented) isLabel = true;
                if (config.dialectParser != null && isLabel) {
                    if (config.dialectParser.recognizeIdiom(tokens, s.label, code)) isLabel = false;
                }
                if (isLabel && (indented || !config.considerCpuOpSymbolsWithoutIndentationToBeLabels)) {
                    if (!config.opParser.getOpSpecs(tokens.get(0)).isEmpty()) isLabel = false;
                }
                for (String keyword : keywordsHintingALabel) {
                    if (isKeyword(tokens.get(1), keyword)) {
                        isLabel = true;
                        break;
                    }
                }
                for (String keyword : keywordsHintingANonScopedLabel) {
                    if (isKeyword(tokens.get(1), keyword)) {
                        isLabel = true;
                        scope = false;
                        break;
                    }
                }
                if (isLabel) {
                    if (config.warning_labelWithoutColon
                            && !tokens.get(1).equals(":=") && !tokens.get(1).equals("=")) {
                        config.warn("Style suggestion", s.fileNameLineString(),
                                "Label " + token + " defined without a colon.");
                    }
                    tokens.remove(0);
                    if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                    if (config.convertSymbolstoUpperCase) token = token.toUpperCase();
                    SourceConstant c = newSourceConstant(token, null, s, previous);
                    if (c == null) {
                        config.error("Problem defining symbol " + labelPrefix + token + " in " + sl);
                        return false;
                    }
                    if (!scope && c.relativeTo == null) {
                        c.name = c.originalName;
                    }
                    s.type = CodeStatement.STATEMENT_NONE;
                    s.label = c;
                    if (defineInCodeBase) {
                        int res = code.addSymbol(c.name, c);
                        if (res == -1) return false;
                        if (res == 0) s.redefinedLabel = true;
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
    
    
    public boolean parseLineInsideMultilineComment(List<String> tokens,
            List<CodeStatement> l, 
            SourceLine sl, CodeStatement s, CodeStatement previous, 
            SourceFile source, CodeBase code) {
        if (s.comment == null) s.comment = "; ";
        while(!tokens.isEmpty()) {
            if (config.tokenizer.isMultiLineCommentEnd(tokens.get(0))) {
                tokens.remove(0);
                insideMultiLineComment = false;
                config.tokenizer.oneTimemultilineCommentStartTokens.clear();
                config.tokenizer.oneTimemultilineCommentEndTokens.clear();
                break;
            }
            s.comment += tokens.remove(0) + " ";
        }
        if (insideMultiLineComment) {
            return true;
        } else {
            return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
    }

            
    public boolean parseRestofTheLine(List<String> tokens,
            List<CodeStatement> l, 
            SourceLine sl, CodeStatement s, CodeStatement previous, 
            SourceFile source, CodeBase code) {
        return parseRestofTheLineInternal(tokens, l, sl, s, previous, true, source, code);
    }
    
    
    public boolean parseRestofTheLineInternal(List<String> tokens,
            List<CodeStatement> l, 
            SourceLine sl, CodeStatement s, CodeStatement previous, 
            boolean allowMoreInstructions,
            SourceFile source, CodeBase code) {
        
        if (tokens.isEmpty()) {
            return true;
        }
        if (tokens.size() == 1 && config.tokenizer.isSingleLineComment(tokens.get(0))) {
            s.comment = tokens.get(0);
            return true;
        }
        if (tokens.size() >= 1 && config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
            tokens.remove(0);
            insideMultiLineComment = true;
            return parseLineInsideMultilineComment(tokens, l, sl, s, previous, source, code);
        }
        
        if (!tokens.isEmpty()) {
            if (allowColonSeparatedInstructions && tokens.get(0).equals(":")) {
                tokens.remove(0);
                CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_NONE, sl, source, config);
                if (labelPrefix != null) s2.labelPrefix = labelPrefix;
                l.add(s2);
                return parseInternal(tokens, l, sl, s2, null, source, code, false);
            } else if (tniAsm045MultipleInstructionsPerLine && tokens.get(0).equals("|")) {
                tokens.remove(0);
                CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_NONE, sl, source, config);
                if (labelPrefix != null) s2.labelPrefix = labelPrefix;
                l.add(s2);
                return parseInternal(tokens, l, sl, s2, null, source, code, false);
            } else if (config.quirk_sjasmplus_dirbol_double_directive ||
                       (tniAsm045MultipleInstructionsPerLine && allowMoreInstructions)) {
                CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_NONE, sl, source, config);
                if (labelPrefix != null) s2.labelPrefix = labelPrefix;
                l.add(s2);
                boolean allowLabels = tniAsm045MultipleInstructionsPerLine && allowMoreInstructions;
                return parseInternal(tokens, l, sl, s2, null, source, code, allowLabels);
            }
        }
               
        config.error("parseRestofTheLine: Cannot parse line " + sl + "  left over tokens: " + tokens);
        return false;
    }
    

    public boolean parseOrg(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {

        Expression exp = config.expressionParser.parse(tokens, s, previous, code);
        if (exp == null) {
            config.error("parseOrg: Cannot parse line " + sl);
            return false;
        }

        // skip optional second argument of .org (last memory address, tniASM syntax)
        if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) {
                tokens.remove(0);
            }
            if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                config.error("parseOrg: Cannot parse line " + sl);
                return false;
            }
            // (for the moment, just ignore the second argument)
            // TODO This should validate that the second argument is an expression (and potentially evaluateToInteger it)
            tokens.clear();
        }

        s.type = CodeStatement.STATEMENT_ORG;
        s.org = exp;
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    
    public boolean parseFpos(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {

        Expression exp = config.expressionParser.parse(tokens, s, previous, code);
        if (exp == null) {
            config.error("parseFpos: Cannot parse line " + sl);
            return false;
        }

        s.type = CodeStatement.STATEMENT_FPOS;
        if (exp.type == Expression.EXPRESSION_PLUS_SIGN ||
            exp.type == Expression.EXPRESSION_SIGN_CHANGE ||
            (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT &&
             exp.integerConstant < 0)) {
            s.fposOffset = exp;
        } else {
            s.fposAbsolute = exp;
        }
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }


    public boolean parseInclude(List<String> tokens,
            List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
        int filePathSearchOrder[] = null;
        if (tokens.size() >= 1) {
            String rawFileName = null;
            String token = tokens.get(0);
            if (config.tokenizer.isString(token)) {
                tokens.remove(0);
                rawFileName = config.tokenizer.stringValue(token);
                
                if (!applyEscapeSequencesToIncludeArguments) {
                    HashMap<String,String> tmp = config.tokenizer.stringEscapeSequences;
                    config.tokenizer.stringEscapeSequences = new HashMap<>();
                    List<String> tokens2 = config.tokenizer.tokenize(sl.line);
                    config.tokenizer.stringEscapeSequences = tmp;
                    for(String token2:tokens2) {
                        if (config.tokenizer.isString(token2)) {
                            rawFileName = config.tokenizer.stringValue(token2);
                            break;
                        }
                    }
                }
                filePathSearchOrder = config.filePathSearchOrder;
            } else if (config.bracketIncludeFilePathSearchOrder != null && token.equals("<")) {
                tokens.remove(0);
                rawFileName = "";
                while (!tokens.isEmpty()) {
                    if (tokens.get(0).equals(">")) {
                        break;
                    }
                    if (config.tokenizer.isSingleLineComment(tokens.get(0))
                        || config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                        break;
                    }
                    rawFileName += tokens.remove(0);
                }
                if (tokens.isEmpty() || !tokens.get(0).equals(">")) {
                    config.error("Expecting include to end in \">\" in " + sl);
                    return false;
                }
                tokens.remove(0);
                filePathSearchOrder = config.bracketIncludeFilePathSearchOrder;
            } else if (allowIncludesWithoutQuotes) {
                rawFileName = "";
                while (!tokens.isEmpty()) {
                    if (config.tokenizer.isSingleLineComment(tokens.get(0))
                        || config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                        break;
                    }
                    rawFileName += tokens.remove(0);
                }
                filePathSearchOrder = config.filePathSearchOrder;
            }
            if (rawFileName != null) {
                // recursive include file:
                String path = resolveIncludePath(rawFileName, source, sl, code, filePathSearchOrder);
                if (path == null) return false;
                SourceFile includedSource = codeBaseParser.parseSourceFile(path, code, source, s);
                if (includedSource == null) {
                    config.error("Problem including file in " + sl);
                    return false;
                } else {
                    s.type = CodeStatement.STATEMENT_INCLUDE;
                    s.rawInclude = rawFileName;
                    s.include = includedSource;
                    return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
                }
            }
        }
        config.error("parseInclude: Cannot parse line " + sl);
        return false;
    }

    public boolean parseIncbin(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
        if (tokens.isEmpty()) {
            config.error("parseIncbin: Cannot parse line " + sl);
            return false;
        }
        String rawFileName = null;
        String token = tokens.get(0);
        int filePathSearchOrder[] = null;
        if (config.tokenizer.isString(token)) {
            tokens.remove(0);
            rawFileName = config.tokenizer.stringValue(token);
            
            if (!applyEscapeSequencesToIncludeArguments) {
                HashMap<String,String> tmp = config.tokenizer.stringEscapeSequences;
                config.tokenizer.stringEscapeSequences = new HashMap<>();
                List<String> tokens2 = config.tokenizer.tokenize(sl.line);
                config.tokenizer.stringEscapeSequences = tmp;
                for(String token2:tokens2) {
                    if (config.tokenizer.isString(token2)) {
                        rawFileName = config.tokenizer.stringValue(token2);
                        break;
                    }
                }
            }
            filePathSearchOrder = config.filePathSearchOrder;
        } else if (config.bracketIncludeFilePathSearchOrder != null && token.equals("<")) {
            tokens.remove(0);
            rawFileName = "";
            while (!tokens.isEmpty()) {
                if (tokens.get(0).equals(">")) {
                    break;
                }
                if (config.tokenizer.isSingleLineComment(tokens.get(0))
                    || config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                    break;
                }
                rawFileName += tokens.remove(0);
            }
            if (tokens.isEmpty() || !tokens.get(0).equals(">")) {
                config.error("Expecting incbin to end in \">\" in " + sl);
                return false;
            }
            tokens.remove(0);
            filePathSearchOrder = config.bracketIncludeFilePathSearchOrder;
        } else if (allowIncludesWithoutQuotes) {
            rawFileName = "";
            while (!tokens.isEmpty()) {
                if (tokens.get(0).equals(",")
                        || config.tokenizer.isSingleLineComment(tokens.get(0))
                        || config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                    break;
                }
                rawFileName += tokens.remove(0);
            }
            filePathSearchOrder = config.filePathSearchOrder;
        }
        if (rawFileName == null) {
            config.error("parseIncbin: Cannot parse line " + sl);
            return false;
        }
        String path = resolveIncludePath(rawFileName, source, sl, code, filePathSearchOrder);
        if (path == null) return false;
        s.type = CodeStatement.STATEMENT_INCBIN;
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
        if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",") || tokens.get(0).equalsIgnoreCase("skip")) {
                tokens.remove(0);
            }
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                skip_exp = config.expressionParser.parse(tokens, s, previous, code);
                if (skip_exp == null) {
                    config.error("parseIncbin: Cannot parse line " + sl);
                    return false;
                }
            }
        }
        if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
            if (tokens.get(0).equals(",")) {
                tokens.remove(0);
            }
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
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
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    public boolean parseEqu(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
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
        
        s.type = CodeStatement.STATEMENT_CONSTANT;
        
        if (s.redefinedLabel) {
            Object n1 = s.label.exp.evaluate(s.label.definingStatement, code, true);
            Object n2 = exp.evaluate(s, code, true);
            if (n1 != null && n2 != null) {
                if (!n1.equals(n2)) {
                    config.error("Redefining label " + s.label.name + " with a different value than previously: " + n1 + " vs " + n2);
                    return false;
                }
            }
            // comment out the line, as this line is useless:
            String comment = "; Commented out by MDL, as this is a redefinition: " + sl.line;
            s.label = null;
            s.comment = comment;
            s.type = CodeStatement.STATEMENT_NONE;
            return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        s.label.exp = exp;
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    public boolean parseData(List<String> tokens, String label, List<CodeStatement> l,
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
        List<Expression> data = new ArrayList<>();
        boolean done = false;

        if (allowEmptyDB_DW_DD_definitions) {
            if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                if (!emptyDB_DW_DD_definitions_define_only_space) {
                    data.add(Expression.constantExpression(0, config));
                } else {
                    data = null;
                }
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
                if (allowdataLinesWithoutCommas) {
                    if (tokens.isEmpty()) {
                        done = true;
                    } else if (config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        done = true;
                    }
                } else {
                    done = true;
                }
            }
        }

        if (isKeyword(label, KEYWORD_DB)) {
            if (emptyDB_DW_DD_definitions_define_only_space && data == null) {
                s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
                s.space = Expression.constantExpression(1, config);
            } else {
                s.type = CodeStatement.STATEMENT_DATA_BYTES;
            }
        } else if (isKeyword(label, KEYWORD_DW)) {
            if (emptyDB_DW_DD_definitions_define_only_space && data == null) {
                s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
                s.space = Expression.constantExpression(2, config);
            } else {
                s.type = CodeStatement.STATEMENT_DATA_WORDS;
            }
        } else {
            if (emptyDB_DW_DD_definitions_define_only_space && data == null) {
                s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
                s.space = Expression.constantExpression(4, config);
            } else {
                s.type = CodeStatement.STATEMENT_DATA_DOUBLE_WORDS;
            }
        }
        if (data != null) {
            s.data = data;

            // Break the data statement into several statements if the use of "$" is detected,
            // as different dialects have different semantics for it (referring to the
            // pinter at the beginning of the statement, or at the current byte):
            List<List<Expression>> all_splits = new ArrayList<>();
            List<Expression> current_split = new ArrayList<>();
            for(Expression exp: s.data) {
                if (exp.containsCurrentAddress()) {
                    // we need to break!
                    if (current_split.isEmpty()) {
                        current_split.add(exp);
                    } else {
                        all_splits.add(current_split);
                        current_split = new ArrayList<>();
                        current_split.add(exp);
                    }
                } else {
                    current_split.add(exp);
                }
            }
            if (!current_split.isEmpty()) all_splits.add(current_split);

            s.data = all_splits.remove(0);
            CodeStatement last = s;
            for(List<Expression> split:all_splits) {
                CodeStatement s2 = new CodeStatement(s.type, sl, source, config);
                s2.data = split;
                l.add(s2);
                last = s2;
            }
            return parseRestofTheLine(tokens, l, sl, last, previous, source, code);
        }
        
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    public boolean parseDefineSpace(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
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
//            s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
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

        s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
        s.space = exp_amount;
        s.space_value = exp_value;
//        }

        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    public boolean parseCPUOp(List<String> tokens, String opName, SourceLine sl,
            List<CodeStatement> l, CodeStatement previous, SourceFile source, CodeBase code) {
        tokens.add(0, opName);
        
        CodeStatement s = l.get(l.size()-1);        
        if (config.dialectParser != null) {
            // put the opName back into the tokens (in case the tokens are modified by the fake CPU op parsing:
            if (!config.dialectParser.parseFakeCPUOps(tokens, sl, l, previous, source, code)) return false;
        }
        
        opName = tokens.remove(0);
        List<Expression> arguments = new ArrayList<>();
        
        List<String> tokensBacktrack = null;
        if (tniAsm045MultipleInstructionsPerLine &&
            config.opParser.isOpName(opName, 0)) {
            tokensBacktrack = new ArrayList<>();
            tokensBacktrack.addAll(tokens);
        }
        
        while (!tokens.isEmpty()) {
            if (config.tokenizer.isSingleLineComment(tokens.get(0)) ||
                (allowColonSeparatedInstructions && tokens.get(0).equals(":")) ||
                (tniAsm045MultipleInstructionsPerLine && tokens.get(0).equals("|"))) {
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
                            && config.tokenizer.isInteger(token.substring(0, token.length() - 1))) {
                        if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                        if (config.convertSymbolstoUpperCase) token = token.toUpperCase();
                        Pair<String, SourceConstant> tmp = config.dialectParser.symbolName(token, s);
                        if (tmp == null) return false;
                        token = tmp.getLeft();
                        exp = Expression.symbolExpression(token, s, code, config);
                        tokens.remove(0);
                    }
                }
            }
            if (exp == null) {
                exp = config.expressionParser.parse(tokens, s, previous, code);
            }
            if (exp == null) {
                if (tokensBacktrack != null) {
                    // Try to see if it's an instruction without arguments, followed by another:
                    arguments.clear();
                    tokens.clear();
                    tokens.addAll(tokensBacktrack);
                    tokensBacktrack = null;
                    break;
                }
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
            
            if (tokensBacktrack != null) {
                // Try to see if it's an instruction without arguments, followed by another:
                arguments.clear();
                tokens.clear();
                tokens.addAll(tokensBacktrack);
                tokensBacktrack = null;
                op_l = config.opParser.parseOp(opName, arguments, s, previous, code);
                if (op_l == null) {
                    config.error("No op spec matches with operator in line " + sl + ", arguments: " + arguments);
                    return false;
                }
            } else {            
                config.error("No op spec matches with operator in line " + sl + ", arguments: " + arguments);
                return false;
            }
        }

        
        s.type = CodeStatement.STATEMENT_CPUOP;
        s.op = op_l.get(0);
        
        for(int i = 1;i<op_l.size();i++) {
            CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, source, config);
            s2.type = CodeStatement.STATEMENT_CPUOP;
            s2.op = op_l.get(i);
            l.add(s2);
        }

        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }
    

    public boolean parseMacroDefinition(List<String> tokens, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {

        // in case someone wrote "macro:" with a colon, ignore the colon:
        if (!tokens.isEmpty() && tokens.get(0).equals(":")) {
            tokens.remove(0);
        }
        
        switch(macroDefinitionStyle) {
            case MACRO_LABEL_MACRO_ARGS:
                if (s.label == null) {
                    config.error("parseMacroDefinition: Cannot parse line " + sl);
                    config.error("Leftover tokens: " + tokens);
                    return false;
                }
                s.label.exp = null; // make sure it's not defined as a label!
                break;
            case MACRO_MACRO_NAME_ARGS:
                {
                    if (s.label != null || tokens.isEmpty()) {
                        config.error("parseMacroDefinition: Cannot parse line " + sl);
                        config.error("Leftover tokens: " + tokens);
                        return false;
                    }
                    String macroNameStr = tokens.remove(0);
                    SourceConstant c = new SourceConstant(macroNameStr, macroNameStr, null, s, config);
                    s.label = c;
                    
                    // remove an optional comma separating macro name and arguments:
                    if (tokens.size()>0 && tokens.get(0).equals(",")) {
                        tokens.remove(0);
                    }
                }
                break;
            case MACRO_BOTH:
                if (s.label != null) {
                    // we have "label: macro args", nothing to do
                } else {
                    // we have "macro name args"
                    if (tokens.isEmpty()) {
                        config.error("parseMacroDefinition: Cannot parse line " + sl);
                        config.error("Leftover tokens: " + tokens);
                        return false;
                    }
                    String macroNameStr = tokens.remove(0);
                    SourceConstant c = new SourceConstant(macroNameStr, macroNameStr, null, s, config);
                    s.label = c;
                    
                    // remove an optional comma separating macro name and arguments:
                    if (tokens.size()>0 && tokens.get(0).equals(",")) {
                        tokens.remove(0);
                    }                    
                }
                break;
            default:
                config.error("parseMacroDefinition: invalid macro style defined for dialect!");
                return false;                
        }        
        
        if (macroKeywordPrecedingArguments != null) {
            if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase(macroKeywordPrecedingArguments)) {
                config.error("Expected '"+macroKeywordPrecedingArguments+"' before macro arguments in " + sl);
                return false;
            }
            tokens.remove(0);
        }
        
        // Parse arguments:
        List<String> args = new ArrayList<>();
        List<Expression> defaultValues = new ArrayList<>();
        while (!tokens.isEmpty()
                && !config.tokenizer.isSingleLineComment(tokens.get(0))
                && !config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
            String token = tokens.remove(0);
            String argName = token;
            if (tniAsmStylemacroArgs && token.equalsIgnoreCase("%") &&
                !tokens.isEmpty()) {
                token = tokens.remove(0);
                if (!token.equalsIgnoreCase("n") && !token.equalsIgnoreCase("s")) {
                    config.error("Unrecognized macro argument type: %" + token + " in " + sl.line);
                    return false;
                }
                argName = "#" + (args.size()+1);
            } else {
                if (macroArguentPrefixes.contains(token) && !tokens.isEmpty()) {
                    argName = token + tokens.remove(0);
                }
            }
            
            args.add(argName);
            
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
        
        s.type = CodeStatement.STATEMENT_MACRO;
        s.macroDefinitionArgs = args;
        s.macroDefinitionDefaults = defaultValues;
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    public boolean parseMacroCall(List<String> tokens, String macroName, List<CodeStatement> l, 
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
        List<Expression> arguments = new ArrayList<>();
        // special case for "IFDEF", which takes a special kind of argument (which should never be
        // evaluated, regardless if it's an eager variable or not):
        boolean isIfDef = false;
        boolean isRept = false;
        
        if (!config.caseSensitiveSymbols) macroName = macroName.toLowerCase();
        if (config.convertSymbolstoUpperCase) macroName = macroName.toUpperCase();
        
        if (config.preProcessor.isMacroName(macroName, config.preProcessor.MACRO_IFDEF) ||
            config.preProcessor.isMacroName(macroName, config.preProcessor.MACRO_IFNDEF)) {
            isIfDef = true;
        }
        if (config.preProcessor.isMacroName(macroName, config.preProcessor.MACRO_REPT)) {
            isRept = true;
        }
        while (!tokens.isEmpty()) {
            if (config.tokenizer.isSingleLineComment(tokens.get(0)) ||
                (allowColonSeparatedInstructions && tokens.get(0).equals(":"))) {
                break;
            }
            Expression exp;
            if (isIfDef) {
                String token = tokens.remove(0);
                if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                if (config.convertSymbolstoUpperCase) token = token.toUpperCase();
                if (config.dialectParser != null) {
                    Pair<String, SourceConstant> tmp = config.dialectParser.symbolName(token, previous);
                    if (tmp == null) return false;
                    token = tmp.getLeft();
                }
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
                if (isRept && reptIndexArgSeparator != null &&
                    !tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(reptIndexArgSeparator) &&
                    arguments.size() == 1) {
                    tokens.remove(0);
                    isRept = false;  // to prevent parsing more than one "reptIndexArgSeparator" in a row
                } else {
                    break;
                }
            }
        }
        
        s.macroCallName = macroName;
        s.macroCallArguments = arguments;
        s.type = CodeStatement.STATEMENT_MACROCALL;
        s.labelPrefix = labelPrefix;
        return parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }

    public String resolveIncludePath(String rawFileName, SourceFile source, SourceLine sl, CodeBase code, int searchOrder[]) {

        // Make sure we don't have a windows/Unix path separator problem:
        if (rawFileName.contains("\\")) {
            rawFileName = rawFileName.replace("\\", File.separator);
        }
        
        config.debug("resolveIncludePath with searchOrder = "+Arrays.toString(searchOrder));
        
        for(int searchSource:searchOrder) {
            switch(searchSource) {
                case MDLConfig.FILE_SEARCH_RELATIVE_TO_INCLUDING_FILE:
                {
                    // Relative to the source file that included this file
                    String sourcePath = source.getPath();
                    if (!sourcePath.isBlank()) {
                        // santi: Do NOT change to "FilenameUtils.concat", that function assumes that the first argument
                        // is an absolute directory, which in different configurations cannot be ensured to be true.
                        // for example when calling mdl like: java -jar mdl.jar ../project/src/main.asm -I ../project2/src
                        final String relativePath = pathConcat(sourcePath, rawFileName);
                        if (Resources.exists(relativePath)) {
                            config.debug("Included file " + rawFileName + " found relative to including source file");
                            return relativePath;
                        }
                    }
                    break;
                }
                case MDLConfig.FILE_SEARCH_ADDITIONAL_PATHS:
                    // Relative to any additional include directories
                    for (File includePath : config.includeDirectories) {
                        // santi: Do NOT change to "FilenameUtils.concat", that function assumes that the first argument
                        // is an absolute directory, which in different configurations cannot be ensured to be true.
                        final String relativePath = pathConcat(includePath.getAbsolutePath(), rawFileName);
                        if (Resources.exists(relativePath)) {
                            config.debug("Included file " + rawFileName + " found relative to include path " + includePath);
                            return relativePath;
                        }
                    }
                    break;
                case MDLConfig.FILE_SEARCH_ORIGINAL_FILE_PATH:
                {
                    // Relative to the main assembler file of the project
                    String sourcePath = code.outputs.get(0).main.getPath();
                    if (!sourcePath.isBlank()) {
                        // santi: Do NOT change to "FilenameUtils.concat", that function assumes that the first argument
                        // is an absolute directory, which in different configurations cannot be ensured to be true.
                        // for example when calling mdl like: java -jar mdl.jar ../project/src/main.asm -I ../project2/src
                        final String relativePath = pathConcat(sourcePath, rawFileName);
                        if (Resources.exists(relativePath)) {
                            config.debug("Included file " + rawFileName + " found relative to main source file");
                            return relativePath;
                        }
                    }
                    break;
                }
                case MDLConfig.FILE_SEARCH_WORKING_DIRECTORY:
                    // Relative to current directory
                    if (Resources.exists(rawFileName)) {
                        config.debug("Included file " + rawFileName + " found relative to current directory");
                        return rawFileName;
                    }
                    break;
            }
        }


        config.error("Cannot find include file \"" + rawFileName + "\" in " + sl.fileNameLineString());
        return null;
    }

    public String pathConcat(String path, String fileName) {
        if (path.endsWith(File.separator) || 
            path.endsWith("/") ||   // we hardcode this one, as otherwise, when running MDL on Windows, File.separator does not match with the "/" characters
            path.isEmpty()) {
            return path + fileName;
        } else {
            return path + File.separator + fileName;
        }
    }

}

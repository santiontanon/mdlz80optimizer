/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package parser.dialects;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.HTMLCodeStyle;
import code.SourceConstant;
import code.SourceFile;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import parser.LineParser;
import parser.SourceLine;

/**
 *
 * @author santi
 */
public class WLADXZ80Dialect implements Dialect {
    
    public static class WLADXSlot {
        public int number;
        public int address;
        public String name;
        
        public WLADXSlot(int a_number, int a_address, String a_name) {
            number = a_number;
            address = a_address;
            name = a_name;
        }
    }
    
    
    public static class WLADXSection {
        public static final int REGULAR_SECTION = 0;
        public static final int RAM_SECTION = 1;
        
        
        public String name;
        public int type;
        public int bank;
        public WLADXSlot slot;
        public int nextAddress = 0;
        
        public WLADXSection(String a_name, int a_type, int a_bank, WLADXSlot a_slot)
        {
            name = a_name;
            type = a_type;
            bank = a_bank;
            slot = a_slot;
            if (slot != null) {
                nextAddress = slot.address;
            }
        }
    }
    
    
    public static class WLADXStruct {
        public String name;
        public SourceFile file;
        public CodeStatement start;
        public List<String> rawAttributeNames = new ArrayList<>();
        public List<String> attributeNames = new ArrayList<>();
        public List<CodeStatement> attributeDefiningStatement = new ArrayList<>();
        public List<Integer> attributeOffset = new ArrayList<>();
        public List<Integer> attributeSize = new ArrayList<>();
        
        
        public WLADXStruct(String a_name, CodeStatement a_start, SourceFile a_file)
        {
            name = a_name;
            start = a_start;
            file = a_file;
        }
    }
    

    private final MDLConfig config;
    
    public static final String ENUM_PRE_LABEL_PREFIX = "__wladx_enum_pre_";
    public static final String ENUM_POST_LABEL_PREFIX = "__wladx_enum_post_";
    public static final String ENDE_LABEL_PREFIX = "__wladx_ende_";
        
    boolean allowReusableLabels = true;
    HashMap<String, Integer> reusableLabelCounts = new HashMap<>();
    
    boolean withingASCIITableDefinition = false;
    HashMap<Integer, Integer> ASCIIMap = new HashMap<>();
    
    // Some lines do not make sense in standard zilog assembler, and MDL removes them,
    // but if we want to generate assembler targetting this dialect, we need those lines.
    List<CodeStatement> linesToKeepIfGeneratingDialectAsm = new ArrayList<>(); 
    List<CodeStatement> auxiliaryStatementsToRemoveIfGeneratingDialectasm = new ArrayList<>();
    
    // Whether we are inside the memory map definition or not:
    public boolean insideMemoryMap = false;
    public WLADXSection currentRamSection = null;
    public WLADXStruct currentStruct = null;
    
    List<WLADXSlot> slots = new ArrayList<>();
    List<WLADXSection> ramSections = new ArrayList<>();
    List<WLADXStruct> structs = new ArrayList<>();

    public Integer enumCounter = null;
    

    /**
     * Constructor
     * @param a_config
     */
    public WLADXZ80Dialect(MDLConfig a_config) {
        super();

        config = a_config;   
        config.expressionParser.allowFloatingPointNumbers = true;
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.emptyDB_DW_DD_definitions_define_only_space = true;
        config.tokenizer.allowDashPlusLabels = true;
        config.eagerMacroEvaluation = true;
        config.allowWLADXSizeOfSymbols = true;
        
        config.lineParser.addKeywordSynonym(".org", config.lineParser.KEYWORD_ORG);        
        config.lineParser.addKeywordSynonym(".include", config.lineParser.KEYWORD_INCLUDE);
        config.lineParser.addKeywordSynonym(".incbin", config.lineParser.KEYWORD_INCBIN);
        config.lineParser.addKeywordSynonym(".db", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".dw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym(".dsb", config.lineParser.KEYWORD_DS);
        
        config.lineParser.keywordsHintingALabel.add("instanceof");
        config.lineParser.keywordsHintingALabel.add("dsb");
        config.lineParser.keywordsHintingALabel.add("dsw");
        
        config.preProcessor.macroSynonyms.put(".if", config.preProcessor.MACRO_IF);
        config.preProcessor.macroSynonyms.put(".else", config.preProcessor.MACRO_ELSE);
        config.preProcessor.macroSynonyms.put(".ifdef", config.preProcessor.MACRO_IFDEF);
        config.preProcessor.macroSynonyms.put(".ifndef", config.preProcessor.MACRO_IFNDEF);
        config.preProcessor.macroSynonyms.put(".endif", config.preProcessor.MACRO_ENDIF);
        config.preProcessor.MACRO_MACRO = ".macro";
        config.preProcessor.MACRO_ENDM = ".endm";
        config.preProcessor.MACRO_REPT = ".repeat";
        config.preProcessor.MACRO_ENDR = ".endr";
        
        config.lineParser.macroDefinitionStyle = LineParser.MACRO_MACRO_NAME_ARGS;     
        config.lineParser.macroKeywordPrecedingArguments = "args";
        config.lineParser.allowdataLinesWithoutCommas = true;
        config.lineParser.reptIndexArgSeparator = "index";
        config.expressionParser.allowSymbolsClashingWithRegisters = true;
    }
    
    
    private SourceConstant getLastAbsoluteLabel(CodeStatement s) 
    {
        // sjasm considers any label as an absolute label, even if it's associated with an equ,
        // so, no need to check if s.label.isLabel() (as in asMSX):
        while(s != null) {
            if (s.label != null && !s.label.originalName.startsWith("_")) {
                return s.label;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
        

    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement s) {
        // A relative label
        if (name.startsWith("@")) {
            SourceConstant lastAbsoluteLabel = getLastAbsoluteLabel(s);        
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.originalName + name, lastAbsoluteLabel);
            }
        } else if (allowReusableLabels && config.tokenizer.isDashPlusLabel(name)) {
            // it's a reusable label:
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            reusableLabelCounts.put(name, count+1);
            name = name.replace("-", "n");
            name = name.replace("+", "p");
            name = config.lineParser.getLabelPrefix() + "_wladx_reusable_" + name + "_" + count;
        }

        // An absolute label
        return Pair.of(config.lineParser.getLabelPrefix() + name, null);
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement s) {
        // A relative label
        if (name.startsWith("@")) {
            SourceConstant lastAbsoluteLabel = getLastAbsoluteLabel(s);
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.originalName + name, lastAbsoluteLabel);
            }
        } else if (allowReusableLabels && config.tokenizer.isDashPlusLabel(name)) {
            // it's a reusable label:            
            int count = 1;
            if (name.charAt(0) == '-') {
                count = reusableLabelCounts.get(name) - 1;
            } else {
                if (reusableLabelCounts.containsKey(name)) {
                    count = reusableLabelCounts.get(name);
                }
            }            
            name = name.replace("-", "n");
            name = name.replace("+", "p");
            return Pair.of("_wladx_reusable_" + name + "_" + count, null);
        }

        // An absolute label
        return Pair.of(name, null);
    }    
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".define")) return true;
        if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase(".def")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".undefine")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".undef")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".bank")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("banks")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("banksize")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("defaultslot")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("slotsize")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".slot")) return true;
        if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("slot")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("bankstotal")) return true;
        if (tokens.size()>=8 && tokens.get(0).equalsIgnoreCase(".sdsctag")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".enum")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".ende")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".section")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".ends")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".asciitable")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".enda")) return true;
        if (tokens.size()>=4 && tokens.get(0).equalsIgnoreCase("map")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".asc")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".incdir")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".memorymap")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".endme")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".ramsection")) return true;
        if (enumCounter == null && tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("instanceof")) return true;
        if (enumCounter == null && tokens.size()>=1 && label != null && label.originalName.equalsIgnoreCase("instanceof")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".struct")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".endst")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".dstruct")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".redefine")) return true;
        
        if (enumCounter != null && tokens.size()>=1 && 
            (tokens.get(0).equalsIgnoreCase("db") ||
             tokens.get(0).equalsIgnoreCase("dw") ||
             tokens.get(0).equalsIgnoreCase("ds") ||
             tokens.get(0).equalsIgnoreCase("dsb") ||
             tokens.get(0).equalsIgnoreCase("dsw") ||
             tokens.get(0).equalsIgnoreCase("instanceof"))) return true;
        return false;
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) 
    {
        if (tokens.size() >= 2 && 
            (tokens.get(0).equalsIgnoreCase(".define") ||
             tokens.get(0).equalsIgnoreCase(".def"))) {
            tokens.remove(0);

            // read variable name:
            String symbolName = tokens.remove(0);
            Expression exp = Expression.constantExpression(0, config);
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
            }
            
            SourceConstant c = config.lineParser.newSourceConstant(symbolName, exp, s, previous);
            if (c == null) {
                config.error("Problem defining symbol " + symbolName + " in " + sl);
                return false;
            }
            int res = code.addSymbol(c.name, c);
            if (res == -1) return false;
            if (res == 0) s.redefinedLabel = true;
            
            s.label = c;
            s.type = CodeStatement.STATEMENT_CONSTANT;
            s.label.exp = exp;            
            
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("export")) {
                // ignore for now
                tokens.remove(0);
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size() >= 2 && 
            (tokens.get(0).equalsIgnoreCase(".undefine") ||
             tokens.get(0).equalsIgnoreCase(".undef"))) {
            tokens.remove(0);
            
            // Get list of symbols to undefine:
            List<Expression> symbols = new ArrayList<>();
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                Expression symbol = config.expressionParser.parse(tokens, s, previous, code);
                symbols.add(symbol);
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                    tokens.remove(0);
                }
            }
            
            for(Expression symbol:symbols) {
                if (symbol.type != Expression.EXPRESSION_SYMBOL) {
                    config.error("Expression " + symbol + " is not a symbol (but "+symbol.type+") in " + sl);
                    return false;
                }                
            }
            
            // Evaluate all the current appearances of this symbol:                
            config.codeBaseParser.resolveSpecificSymbols(code, symbols);
            
            for(Expression symbol:symbols) {
                SourceConstant c = code.getSymbol(symbol.symbolName);
                // remove the defining statement of this symbol:
                if (c.definingStatement != null) {
                    c.definingStatement.source.getStatements().remove(c.definingStatement);
                }
                code.removeSymbol(symbol.symbolName);
                
            }
                
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".bank")) {
            // ignore for now:
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse bank expression in " + sl);
                return false;
            }
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("slot")) {
                tokens.remove(0);
                Expression exp2 = config.expressionParser.parse(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Cannot parse slot expression in " + sl);
                    return false;
                }
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("banks")) {
            // ignore for now:
            return ignoreWithParameters(1, false, tokens, l, sl, s, previous, source, code);
        }        
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("banksize")) {
            // ignore for now:
            return ignoreWithParameters(1, false, tokens, l, sl, s, previous, source, code);
        }        
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defaultslot")) {
            // ignore for now:
            return ignoreWithParameters(1, false, tokens, l, sl, s, previous, source, code);
        }        

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("slotsize")) {
            // ignore for now:
            return ignoreWithParameters(1, false, tokens, l, sl, s, previous, source, code);
        }        

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".slot")) {
            // ignore for now:
            return ignoreWithParameters(1, false, tokens, l, sl, s, previous, source, code);
        }        
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("bankstotal")) {
            // ignore for now:
            return ignoreWithParameters(1, false, tokens, l, sl, s, previous, source, code);
        }           
        
        if (tokens.size() >= 8 && tokens.get(0).equalsIgnoreCase(".sdsctag")) {
            // ignore for now:
            // .SDSCTAG {version number}, {program name}, {program release notes}, {program author}
            return ignoreWithParameters(4, true, tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".section")) {
            tokens.remove(0);

            String name = tokens.remove(0);
            
            if (!tokens.isEmpty()) {
                if (tokens.get(0).equalsIgnoreCase("force")) {
                    tokens.remove(0);
                } else if (tokens.get(0).equalsIgnoreCase("free")) {
                    tokens.remove(0);
                } else if (tokens.get(0).equalsIgnoreCase("semifree")) {
                    tokens.remove(0);
                } else if (tokens.get(0).equalsIgnoreCase("align")) {
                    tokens.remove(0);
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp == null) {
                        config.error("Cannot parse align expression in " + sl);
                        return false;
                    }
                    CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_DEFINE_SPACE,sl, source, config);
                    // ds (((($-1)/exp)+1)*exp-$)
                    s2.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                                  Expression.parenthesisExpression(
                                    Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                                      Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                                        Expression.parenthesisExpression(
                                          Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                                              Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), 
                                              Expression.constantExpression(1, config), config), "(", config),
                                          exp, config), 
                                      Expression.constantExpression(1, config), config), "(", config), 
                                  exp, config),
                                Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), config);
                    s2.space_value = Expression.constantExpression(0, config);
                    l.add(s2);
                    auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s2);
                }
            }
            
            currentRamSection = new WLADXSection(name, WLADXSection.REGULAR_SECTION, 0, null);
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".asciitable")) {
            tokens.remove(0);
            withingASCIITableDefinition = true;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".enda")) {
            tokens.remove(0);
            withingASCIITableDefinition = false;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size() >= 4 && tokens.get(0).equalsIgnoreCase("map")) {
            tokens.remove(0);
            if (!withingASCIITableDefinition) {
                config.error("'map' found outside of .asciitable in " + sl);
                return false;                
            }
            
            Expression expStart = config.expressionParser.parse(tokens, s, previous, code);
            Expression expEnd = null;
            Expression expStartInt = null;
            if (expStart == null) {
               config.error("Cannot parse expression in " + sl);
               return false;
            }
            if (tokens.isEmpty()) {
                if (expStart.type == Expression.EXPRESSION_EQUAL) {
                    expStartInt = expStart.args.get(1);
                    expStart = expStart.args.get(0);
                } else {
                    config.error("Expected '=' or 'to' in " + sl);
                    return false;                
                }
            } else if (tokens.get(0).equalsIgnoreCase("to")) {
                tokens.remove(0);
                expEnd = config.expressionParser.parse(tokens, s, previous, code);
                if (expEnd == null) {
                   config.error("Cannot parse expression in " + sl);
                   return false;
                }
            }
            if (expStartInt == null) {
                if (!tokens.isEmpty() && tokens.get(0).equals("=")) {
                    tokens.remove(0);
                    expStartInt = config.expressionParser.parse(tokens, s, previous, code);
                    if (expStartInt == null) {
                       config.error("Cannot parse expression in " + sl);
                       return false;
                    }
                } else if (expEnd != null && expEnd.type == Expression.EXPRESSION_EQUAL) {
                    expStartInt = expEnd.args.get(1);
                    expEnd = expEnd.args.get(0);
                } else if (expStart != null && expStart.type == Expression.EXPRESSION_EQUAL) {
                    expStartInt = expStart.args.get(1);
                    expStart = expStart.args.get(0);
                }
            }

            // Create the mapping:
            Integer startValue = expStart.evaluateToInteger(s, code, true);
            if (startValue == null) {
                config.error("Cannot evaluate " +expStart+ " to a single character in " + sl);
                return false;                
            }
            Integer endValue = null;
            if (expEnd != null) {
                endValue = expEnd.evaluateToInteger(s, code, true);
                if (endValue == null) {
                    config.error("Cannot evaluate " +expEnd+ " to a single character in " + sl);
                    return false;
                }
            }
            Integer startValueInt = expStartInt.evaluateToInteger(s, code, true);
            if (startValueInt == null) {
                config.error("Cannot evaluate " +expStartInt+ " to an integer in " + sl);
                return false;                
            }
            
            int c1 = startValue;
            int c2 = c1;
            if (endValue != null) {
                c2 = endValue;
            }
            for(int i = c1;i<=c2;i++) {
                ASCIIMap.put(i, startValueInt);
                startValueInt ++;
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".asc")) {
            tokens.remove(0);
            
            if (!config.lineParser.parseData(tokens, config.lineParser.KEYWORD_DB, l, sl, s, previous, source, code)) {
                return false;                
            }
            
            for(CodeStatement s2:l) {
                List<Expression> data2 = new ArrayList<>();
                for(Expression exp:s2.data) {
                    if (exp.evaluatesToStringConstant()) {
                        // apply the ASCII mapping:
                        String str = exp.evaluateToString(s2, code, allowReusableLabels);
                        for(int i = 0;i<str.length();i++) {
                            int c = str.charAt(i);
                            if (ASCIIMap.containsKey(c)) {
                                data2.add(Expression.constantExpression(ASCIIMap.get(c), config));
                            } else {
                                data2.add(Expression.constantExpression(c, config));
                            }
                        }
                    } else {
                        data2.add(exp);
                    }
                }
                s2.data = data2;
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".incdir")) {
            tokens.remove(0);
            
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (!exp.evaluatesToStringConstant()) {
                config.error("Expected string in " + sl);
                return false;
            }
            String path = exp.evaluateToString(s, code, true);
            File file = new File(path).getAbsoluteFile();
            if (file.exists()) {
                config.includeDirectories.add(file);
                return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
            }

            String sourcePath = source.getPath();
            if (StringUtils.isNotBlank(sourcePath)) {
                // santi: Do NOT change to "FilenameUtils.concat", that function assumes that the first argument
                // is an absolute directory, which in different configurations cannot be ensured to be true.
                // for example when calling mdl like: java -jar mdl.jar ../project/src/main.asm -I ../project2/src
                final String relativePath = config.lineParser.pathConcat(sourcePath, path);
                file = new File(relativePath).getAbsoluteFile();
                if (file.exists()) {
                    config.includeDirectories.add(file);
                    return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
                }
            }
            config.error("Cannot find path " + path + " in " + sl);
            return false;
        }
        
        
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".memorymap")) {
            insideMemoryMap = true;
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".endme")) {
            if (!insideMemoryMap) {
                config.error(".endme found outside of a memory map definition in " + sl);
                return false;
            }
            insideMemoryMap = false;
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("slot")) {
            if (!insideMemoryMap) {
                config.error("'slot' found outside of a memory map definition in " + sl);
                return false;
            }
            tokens.remove(0);
            Expression slotNumber_exp = config.expressionParser.parse(tokens, s, previous, code);
            if (slotNumber_exp == null) {
                config.error("Cannot parse slot number in " + sl);
                return false;
            }
            Expression slotAddress_exp = config.expressionParser.parse(tokens, s, previous, code);
            if (slotAddress_exp == null) {
                config.error("Cannot parse slot address in " + sl);
                return false;
            }
            String slotName = null;
            if (!tokens.isEmpty() && config.tokenizer.isString(tokens.get(0))) {
                Expression slotName_exp = config.expressionParser.parse(tokens, s, previous, code);
                if (slotName_exp == null || !slotName_exp.evaluatesToStringConstant()) {
                    config.error("Cannot parse slot name in " + sl);
                    return false;
                }
                slotName = slotName_exp.evaluateToString(s, code, true);
            }
            slots.add(new WLADXSlot(slotNumber_exp.evaluateToInteger(s, code, true),
                                    slotAddress_exp.evaluateToInteger(s, code, true), slotName));
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        
        
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".ramsection")) 
        {
            tokens.remove(0);
            
            if (tokens.isEmpty() || !config.tokenizer.isString(tokens.get(0))) {
                config.error("Cannot parse ramsectino name in " + sl);
                return false;                
            }
            String name = tokens.remove(0);  
            Integer bank = 0;
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("bank")) {
                tokens.remove(0);
                Expression bank_exp = config.expressionParser.parse(tokens, s, previous, code);
                bank = bank_exp.evaluateToInteger(s, code, true);
                if (bank == null) {
                    config.error("cannot evaluate bank expression in " + sl);
                    return false;                
                }
            }
            if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("slot")) {
                config.error("cannot parse slot in " + sl);
                return false;                
            }
            tokens.remove(0);
            Expression slot_exp = config.expressionParser.parse(tokens, s, previous, code);
            WLADXSlot slot = null;
            if (slot_exp.evaluatesToStringConstant()) {
                // slot name:
                String slot_name = slot_exp.evaluateToString(s, code, true);
                for(WLADXSlot slot2:slots) {
                    if (slot2.name != null && slot2.name.equals(slot_name)) {
                        slot = slot2;
                        break;
                    }
                }
                if (slot == null) {
                    config.error("unknown slot '"+slot_name+"' in " + sl);
                    return false;                
                }
            } else if (slot_exp.evaluatesToIntegerConstant()) {
                int number_or_address = slot_exp.evaluateToInteger(s, code, true);
                // slot number or address:
                for(WLADXSlot slot2:slots) {
                    if (slot2.number == number_or_address ||
                        slot2.address == number_or_address) {
                        slot = slot2;
                        break;
                    }
                }
                if (slot == null) {
                    config.error("unknown slot '"+number_or_address+"' in " + sl);
                    return false;                
                }
            } else {
                config.error("unknown slot in " + sl);
                return false;                
            }
            
            if (currentRamSection != null) {
                config.error("RAM section started before previous ("+currentRamSection.name+") ended in " + sl);
                return false;
            }
            
            currentRamSection = new WLADXSection(name, WLADXSection.RAM_SECTION, bank, slot);
            ramSections.add(currentRamSection);
                        
            s.type = CodeStatement.STATEMENT_ORG;
            s.org = Expression.constantExpression(slot.address, config);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);            
        }
        
        
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".ends")) {
            currentRamSection = null;
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }


        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".struct")) {
            tokens.remove(0);
            if (tokens.isEmpty()) {
                config.error("Missing struct name in " + sl);
                return false;
            }
            String name = tokens.remove(0);
            currentStruct = new WLADXStruct(name, s, source);
            structs.add(currentStruct);
            config.lineParser.pushLabelPrefix(currentStruct.name + ".");

            s.type = CodeStatement.STATEMENT_CONSTANT;
            s.label = new SourceConstant(name, name, Expression.constantExpression(0, config), s, config);
            code.addSymbol(name, s.label);

            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".endst")) {
            tokens.remove(0);
            if (currentStruct.file == null) {
                config.error("ends outside of a struct in " + sl);
                return false;
            }
            if (currentStruct.file != source) {
                config.error("struct split among multiple files is not supported in " + sl);
                return false;
            }
            
            if (!endStructDefinition(sl, source, code)) return false;

            config.lineParser.popLabelPrefix();
            currentStruct = null;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        

        if (enumCounter == null &&
            ((tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("instanceof")) ||
             (tokens.size()>=1 && s.label != null && s.label.originalName.equalsIgnoreCase("instanceof")))) {
            
            // instantiating a struct:
            if (s.label != null && s.label.originalName.equalsIgnoreCase("instanceof")) {
                code.removeSymbol(s.label.name);
                s.label = null;
            } else {
                tokens.remove(0);
            }
            
            String structName = tokens.remove(0);
            WLADXStruct struct = null;
            for(WLADXStruct struct2:structs) {
                if (struct2.name.equals(structName)) {
                    struct = struct2;
                    break;
                }
            }
            if (struct == null) {
                config.error("Unknown struct in " + sl);
                return false;
            }
            
            if (currentStruct != null) {
                // add to the current struct:
                // Add a mark that will be processed later:
                CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_NONE, sl, source, config);
                if (s.label != null) {
                    s2.comment += "mdl-wladx-struct-mark\t" + struct.name + "\t" + s.label.originalName;
                } else {
                    s2.comment += "mdl-wladx-struct-mark\t" + struct.name;
                }
                s2.type = CodeStatement.STATEMENT_NONE;
                l.add(s2);
            } else {
                // instantiate struct:
                if (s.label != null) {
                    s.type = CodeStatement.STATEMENT_CONSTANT;
                    s.label.exp = Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config);
                    s.label.clearCache();
                }
                
                for(int i = 0;i<struct.attributeNames.size();i++) {    
                    int offset = struct.attributeOffset.get(i);
                    CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_CONSTANT, sl, source, config);
                    SourceConstant c = new SourceConstant(
                            s.label.name + "." + struct.rawAttributeNames.get(i),
                            s.label.originalName + "." + struct.rawAttributeNames.get(i), 
                            Expression.operatorExpression(Expression.EXPRESSION_SUM,
                                    Expression.symbolExpression(s.label.name, s2, code, config),
                                    Expression.constantExpression(offset, config), config),
                            s2, config);
                    s2.label = c;
                    int res = code.addSymbol(c.name, c);
                    if (res == -1) return false;
                    if (res == 0) s.redefinedLabel = true; 
                    l.add(s2);
                }   
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".dstruct")) {
            // example:
            // .dstruct ButterflyStepsLevel1 instanceof StepsData data 24, ScrollTable2440, 0, 111, 5, 0
            tokens.remove(0);
            String labelPrefix = tokens.remove(0);       
            
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("instanceof")) {
                tokens.remove(0);
            }
            
            String structName = tokens.remove(0);
            WLADXStruct struct = null;
            for(WLADXStruct struct2:structs) {
                if (struct2.name.equals(structName)) {
                    struct = struct2;
                    break;
                }
            }
            if (struct == null) {
                config.error("Unknown struct in " + sl);
                return false;
            }
            
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("data")) {
                tokens.remove(0);
            }
            
            for(int i = 0;i<struct.attributeNames.size();i++) {
                Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("Cannot parse expression for attribute " + struct.attributeNames.get(i) + " in " + sl);
                    return false;
                }
                int type = CodeStatement.STATEMENT_DATA_BYTES;
                switch(struct.attributeSize.get(i)) {
                    case 1:
                        type = CodeStatement.STATEMENT_DATA_BYTES;
                        break;
                    case 2:
                        type = CodeStatement.STATEMENT_DATA_WORDS;
                        break;
                    case 4:
                        type = CodeStatement.STATEMENT_DATA_DOUBLE_WORDS;
                        break;
                    default:
                        config.error("Unsupported size " + struct.attributeSize.get(i) + " in struct in " + sl);
                        return false;
                }
                CodeStatement s2 = new CodeStatement(type, sl, source, config);
                String label = labelPrefix + "." + struct.attributeNames.get(i);
                SourceConstant sc = new SourceConstant(label, label, Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), s2, config);
                code.addSymbol(label, sc);
                s2.data = new ArrayList<>();
                s2.data.add(exp);
                l.add(s2);
                
                if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(",")) {
                    tokens.remove(0);
                }
                
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".enum")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Could not parse enum address in " + sl);
                return false;
            }
            if (!exp.evaluatesToIntegerConstant()) {
                config.error("Enum address does not evaluate to a numerical constant in " + sl);
                return false;
            }
            
            enumCounter = exp.evaluateToInteger(s, code, true);
            if (enumCounter == null) {
                config.error("Error evaluating enum address in " + sl);
                return false;
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".ende")) {
            tokens.remove(0);
            enumCounter = null;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (enumCounter != null && tokens.size()>=1 && 
            (tokens.get(0).equalsIgnoreCase("db") ||
             tokens.get(0).equalsIgnoreCase("dw") ||
             tokens.get(0).equalsIgnoreCase("ds") ||
             tokens.get(0).equalsIgnoreCase("dsb") ||
             tokens.get(0).equalsIgnoreCase("dsw") ||
             tokens.get(0).equalsIgnoreCase("instanceof"))) {
            String keyword = tokens.remove(0).toLowerCase();

            if (s.label == null) {
                config.error("label missing in enum statement in " + sl);
                return false;
            }
            
            switch(keyword) {
                case "db":
                    s.type = CodeStatement.STATEMENT_CONSTANT;
                    s.label.exp = Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config);
                    enumCounter += 1;
                    break;
                    
                case "dw":
                    s.type = CodeStatement.STATEMENT_CONSTANT;
                    s.label.exp = Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config);
                    enumCounter += 2;
                    break;
                    
                case "ds":
                case "dsb":
                {
                    s.type = CodeStatement.STATEMENT_CONSTANT;
                    s.label.exp = Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config);
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp == null || !exp.evaluatesToIntegerConstant()) {
                        config.error("Cannot parse space expression in " + sl);
                        return false;
                    }
                    Integer space = exp.evaluateToInteger(s, code, true);
                    if (space == null) {
                        config.error("Cannot evaluate space expression in " + sl);
                        return false;
                    }
                    enumCounter += space;
                    break;
                }
                
                case "dsw":
                {
                    s.type = CodeStatement.STATEMENT_CONSTANT;
                    s.label.exp = Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config);
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp == null || !exp.evaluatesToIntegerConstant()) {
                        config.error("Cannot parse space expression in " + sl);
                        return false;
                    }
                    Integer space = exp.evaluateToInteger(s, code, true);
                    if (space == null) {
                        config.error("Cannot evaluate space expression in " + sl);
                        return false;
                    }
                    enumCounter += space * 2;
                    break;
                }
                
                case "instanceof":
                {
                    String structName = tokens.remove(0);
                    WLADXStruct struct = null;
                    for(WLADXStruct struct2:structs) {
                        if (struct2.name.equals(structName)) {
                            struct = struct2;
                            break;
                        }
                    }
                    if (struct == null) {
                        config.error("Unknown struct in " + sl);
                        return false;
                    }
                    
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    Integer number = null;
                    if (exp != null) {
                        if (!exp.evaluatesToIntegerConstant()) {
                            config.error("Cannot evaluate count expression in " + sl);
                            return false;
                        }
                        number = exp.evaluateToInteger(s, code, true);
                        if (number == null) {
                            config.error("Cannot evaluate count expression in " + sl);
                            return false;
                        }
                    }

                    s.type = CodeStatement.STATEMENT_CONSTANT;
                    s.label.exp = Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config);
                    for(int count = 0;count<(number == null ? 1:number);count++) {
                        String prefix = s.label.name + "." + (count+1);
                        String originalPrefix = s.label.originalName + "." + (count+1);
                        
                        if (number == null) {
                            prefix = s.label.name;
                            originalPrefix = s.label.originalName;
                        } else {
                            CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_CONSTANT, sl, source, config);
                            SourceConstant c = new SourceConstant(
                                    prefix,
                                    originalPrefix, 
                                    Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config), s2, config);
                            s2.label = c;
                            int res = code.addSymbol(c.name, c);
                            if (res == -1) return false;
                            if (res == 0) s.redefinedLabel = true; 
                            l.add(s2);
                        }
                        
                        for(int i = 0;i<struct.attributeNames.size();i++) {    
                            CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_CONSTANT, sl, source, config);
                            SourceConstant c = new SourceConstant(
                                    prefix + "." + struct.rawAttributeNames.get(i),
                                    originalPrefix + "." + struct.rawAttributeNames.get(i), 
                                    Expression.constantExpression(enumCounter, Expression.RENDER_AS_16BITHEX, config), s2, config);
                            s2.label = c;
                            int res = code.addSymbol(c.name, c);
                            if (res == -1) return false;
                            if (res == 0) s.redefinedLabel = true; 
                            l.add(s2);
                            int size = struct.attributeSize.get(i);
                            enumCounter += size;
                        }   
                    }
                    
                    break;
                }
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".redefine")) {
            tokens.remove(0);
            
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode in " + sl);
                return false;
            }
            
            String symbolName = tokens.remove(0);
            SourceConstant c = code.getSymbol(symbolName);
            if (c == null) {
                config.error("Cannot resolve eager variable named '"+symbolName+"' in " + sl);
                return false;
            }

            
            // If the variable was used before, evaluate it:
            if (!c.resolveEagerly) {
                List<String> symbols = new ArrayList<>();
                symbols.add(symbolName);
                config.codeBaseParser.resolveSpecificSymbolsFromString(code, symbols);

                c.clearCache();
                c.resolveEagerly = true;
                // remove the defining statement of this symbol:
                if (c.definingStatement != null) {
                    c.definingStatement.source.getStatements().remove(c.definingStatement);
                }
            }
            
            Expression newValue = config.expressionParser.parse(tokens, s, previous, code);
            if (newValue == null) {
                config.error("Cannot parse new value expression in " + sl);
                return false;
            }
            Integer value = newValue.evaluateToInteger(s, code, false, previous);
            if (value == null) {
                config.error("Cannot evaluate new value expression in " + sl);
                return false;
            }
            Expression exp = Expression.constantExpression(value, config);
            c.exp = exp;
            c.clearCache();
            
            // these variables should not be part of the source code:
            l.clear();
            return true;
        }            
        
        
        return false;
    }
    
    
    public boolean endStructDefinition(SourceLine sl, SourceFile source, CodeBase code)
    {
        // Transform the struct into equ definitions with local labels:
        int offset = 0;
        int start = source.getStatements().indexOf(currentStruct.start) + 1;
        for (int i = start; i < source.getStatements().size(); i++) {
            CodeStatement s2 = source.getStatements().get(i);
            int offset_prev = offset;
            switch (s2.type) {
                case CodeStatement.STATEMENT_NONE:
                    if (s2.comment != null && s2.comment.contains("mdl-wladx-struct-mark")) {
                        // insert struct:
                        String markTokens[] = s2.comment.split("\t");
                        s2.comment = null;
                        String structName = markTokens[1];
                        String attributeLabel = "";
                        if (markTokens.length >= 3) {
                            attributeLabel = markTokens[2] + ".";
                        }
                        WLADXStruct struct = null;
                        for(WLADXStruct struct2:structs) {
                            if (struct2.name.equals(structName)) {
                                struct = struct2;
                                break;
                            }
                        }
                        
                        for(int j = 0;j<struct.attributeNames.size();j++) {    
                            currentStruct.rawAttributeNames.add(attributeLabel + struct.rawAttributeNames.get(j));
                            currentStruct.attributeNames.add(attributeLabel + struct.attributeNames.get(j));
                            currentStruct.attributeDefiningStatement.add(struct.attributeDefiningStatement.get(j));
                            currentStruct.attributeOffset.add(offset);
                            currentStruct.attributeSize.add(struct.attributeSize.get(j));
                            offset += struct.attributeSize.get(j);
                        }
                    }
                    break;
                case CodeStatement.STATEMENT_DATA_BYTES:
                case CodeStatement.STATEMENT_DATA_WORDS:
                case CodeStatement.STATEMENT_DATA_DOUBLE_WORDS:
                case CodeStatement.STATEMENT_DEFINE_SPACE:
                {
                    int size = s2.sizeInBytes(code, true, true, true);
                    if (s2.label != null) {
                        currentStruct.rawAttributeNames.add(s2.label.originalName);
                        currentStruct.attributeNames.add(s2.label.name);
                        // update the original name of the struct field:
                        s2.label.originalName = s2.label.name;
                    } else {
                        currentStruct.rawAttributeNames.add(null);
                        currentStruct.attributeNames.add(null);
                    }
                    currentStruct.attributeDefiningStatement.add(s2);
                    currentStruct.attributeOffset.add(offset);
                    currentStruct.attributeSize.add(size);
                    offset += size;
                    break;
                }
                default:
                    config.error("Unsupported statement (type="+s2.type+") inside a struct definition in " + sl);
                    return false;
            }
            if (s2.label != null) {
                s2.type = CodeStatement.STATEMENT_CONSTANT;
                s2.label.exp = Expression.constantExpression(offset_prev, config);
            } else {
                s2.type = CodeStatement.STATEMENT_NONE;
            }                
        }

        // Record the struct for later:
        currentStruct.start.label.exp = Expression.constantExpression(offset, config);
        structs.add(currentStruct);
        config.lineParser.keywordsHintingALabel.add(currentStruct.name);
        return true;
    }    
    
    
    public boolean ignoreWithParameters(int nParameters, boolean commas, List<String> tokens, List<CodeStatement> l, SourceLine sl, CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
        tokens.remove(0);
        for(int i = 0;i<nParameters;i++) {
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            if (commas) {
                if (i < nParameters - 1) {
                    if (tokens.isEmpty() || !tokens.get(0).equals(",")) {
                        config.error("Expected ',' after expression in " + sl);
                        return false;
                    }
                    tokens.remove(0);
                }
            }
        }
        return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
    }
    
    
    @Override
    public boolean postParseActions(CodeBase code)
    {
        if (!reusableLabelCounts.isEmpty() && config.warning_ambiguous) {
            config.warn("Use of wladx reusable labels, which are conducive to human error.");
        }
        
        // Make sure all reusable labels are replaced by the MDL-generated names (since after
        // optimizations, they could have moved around, and their original order might be lost):
        for(String s:code.getSymbols()) {
            SourceConstant c = code.getSymbol(s);
            if (config.tokenizer.isInteger(c.originalName)) {
                c.originalName = c.name;
            }
        }
       
        return postCodeModificationActions(code);
    }
            
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code, Path rootPath, HTMLCodeStyle style) {
        boolean useOriginalNames = true;
        if (linesToKeepIfGeneratingDialectAsm.contains(s)) {
            return s.sl.line;
        }

        if (auxiliaryStatementsToRemoveIfGeneratingDialectasm.contains(s)) return null;
        

        return s.toStringUsingRootPath(rootPath, useOriginalNames, true, code, style);
    }    
            
}

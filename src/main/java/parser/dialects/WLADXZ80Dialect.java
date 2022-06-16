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
import parser.SourceLine;

/**
 *
 * @author santi
 */
public class WLADXZ80Dialect implements Dialect {

    private final MDLConfig config;
    
    public static final String ENUM_PRE_LABEL_PREFIX = "__wladx_enum_pre_";
    public static final String ENUM_POST_LABEL_PREFIX = "__wladx_enum_post_";
    public static final String ENDE_LABEL_PREFIX = "__wladx_ende_";
    
    List<CodeStatement> enumStatements = new ArrayList<>();
    List<CodeStatement> endeStatements = new ArrayList<>();
    
    boolean allowReusableLabels = true;
    HashMap<String, Integer> reusableLabelCounts = new HashMap<>();
    
    boolean withingASCIITableDefinition = false;
    HashMap<Integer, Integer> ASCIIMap = new HashMap<>();
    
    // Some lines do not make sense in standard zilog assembler, and MDL removes them,
    // but if we want to generate assembler targetting this dialect, we need those lines.
    List<CodeStatement> linesToKeepIfGeneratingDialectAsm = new ArrayList<>(); 
    List<CodeStatement> auxiliaryStatementsToRemoveIfGeneratingDialectasm = new ArrayList<>();
    
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
        config.lineParser.allowDashPlusLabels = true;
        config.eagerMacroEvaluation = true;
        config.allowWLADXSizeOfSymbols = true;
        
        config.lineParser.addKeywordSynonym(".org", config.lineParser.KEYWORD_ORG);        
        config.lineParser.addKeywordSynonym(".include", config.lineParser.KEYWORD_INCLUDE);
        config.lineParser.addKeywordSynonym(".incbin", config.lineParser.KEYWORD_INCBIN);
        config.lineParser.addKeywordSynonym(".db", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".dw", config.lineParser.KEYWORD_DW);
        
        config.preProcessor.macroSynonyms.put(".if", config.preProcessor.MACRO_IF);
        config.preProcessor.macroSynonyms.put(".else", config.preProcessor.MACRO_ELSE);
        config.preProcessor.macroSynonyms.put(".ifdef", config.preProcessor.MACRO_IFDEF);
        config.preProcessor.macroSynonyms.put(".ifndef", config.preProcessor.MACRO_IFNDEF);
        config.preProcessor.macroSynonyms.put(".endif", config.preProcessor.MACRO_ENDIF);        
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
                    config.error("Expression " + symbol + " is not a symbol in " + sl);
                    return false;
                }
                // Do not undefine the symbols for now, as this assumes eager
                // expression evaluation, while MDL uses lazy evaluation.
                // TO DO: Perhaps the solution would be to go through the code
                // base evaluating all the expressions that contain these
                // symbols eagerly.
                // code.removeSymbol(symbol.symbolName);
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

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("slot")) {
            // ignore for now:
            return ignoreWithParameters(2, false, tokens, l, sl, s, previous, source, code);
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

        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".enum")) {
            tokens.remove(0);
            
            if (s.label != null) {
                // if there was a label in the "phase" line, create a new one:
                s = new CodeStatement(CodeStatement.STATEMENT_ORG, new SourceLine(sl), source, config);
                l.add(s);
            }
            
            // parse as an "org":
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse phase address in " + sl);
                return false;
            } 
            enumStatements.add(s);
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            // Add the label before the org:
            String phase_pre_label_name = ENUM_PRE_LABEL_PREFIX + enumStatements.size();
            s.type = CodeStatement.STATEMENT_ORG;
            s.org = exp;
            s.label = new SourceConstant(phase_pre_label_name, phase_pre_label_name, 
                                         Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config),
                                         s, config);
            code.addSymbol(phase_pre_label_name, s.label);
            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s);
            
            // Add the label after the org:
            s = new CodeStatement(CodeStatement.STATEMENT_NONE, new SourceLine(sl), source, config);
            l.add(s);
            String phase_post_label_name = ENUM_POST_LABEL_PREFIX + enumStatements.size();
            s.label = new SourceConstant(phase_post_label_name, phase_post_label_name, 
                                         Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config),
                                         s, config);
            code.addSymbol(phase_post_label_name, s.label);
            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".ende")) {
            tokens.remove(0);
            
            if (s.label != null) {
                // if there was a label in the "phase" line, create a new one:
                s = new CodeStatement(CodeStatement.STATEMENT_ORG, sl, source, config);
                l.add(s);
                auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s);
            }            

            // restore normal mode addressing:
            String phase_pre_label_name = ENUM_PRE_LABEL_PREFIX + enumStatements.size();
            String phase_post_label_name = ENUM_POST_LABEL_PREFIX + enumStatements.size();
            String dephase_label_name = ENDE_LABEL_PREFIX + enumStatements.size();

            // __asmsx_phase_pre_* + (__asmsx_dephase_* - __asmsx_phase_post_*)
            Expression exp = Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                    Expression.symbolExpression(phase_pre_label_name, s, code, config),
                    Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                    Expression.symbolExpression(dephase_label_name, s, code, config),
                                    Expression.symbolExpression(phase_post_label_name, s, code, config), config), 
                            "(", config), config);
            
            s.type = CodeStatement.STATEMENT_ORG;
            s.org = exp;
            s.label = new SourceConstant(dephase_label_name, dephase_label_name, 
                                         Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config),
                                         s, config);
            code.addSymbol(dephase_label_name, s.label);
            endeStatements.add(s);
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".section")) {
            tokens.remove(0);
            // Ignore for now ...
            Expression expName = config.expressionParser.parse(tokens, s, previous, code);
            if (expName == null) {
                config.error("Cannot parse section name in " + sl);
                return false;
            } 
            
            if (!tokens.isEmpty()) {
                if (tokens.get(0).equalsIgnoreCase("force")) {
                    tokens.remove(0);
                } else if (tokens.get(0).equalsIgnoreCase("free")) {
                    tokens.remove(0);
                } else if (tokens.get(0).equalsIgnoreCase("semifree")) {
                    tokens.remove(0);
                }
            }

            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase(".ends")) {
            tokens.remove(0);
            // Ignore for now ...
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
        
        
        return false;
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
        if (reusableLabelCounts.size() > 0 && config.warning_ambiguous) {
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

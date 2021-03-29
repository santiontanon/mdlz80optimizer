/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import cl.MDLConfig;
import code.CPUOpSpec;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parser.SourceLine;

/**
 *
 * @author santi
 */
public abstract class SjasmDerivativeDialect implements Dialect {
    
    
    public static class SjasmStruct {
        String name;
        SourceFile file;
        CodeStatement start;
        List<String> rawAttributeNames = new ArrayList<>();
        List<String> attributeNames = new ArrayList<>();
        List<Integer> attributeCodeStatementTypes = new ArrayList<>();
        List<Expression> attributeDefaults = new ArrayList<>();
        List<CodeStatement> attributeDefiningStatement = new ArrayList<>();
    }
        
    
    MDLConfig config;
    
    List<String> forbiddenLabelNames = new ArrayList<>();
    
    SjasmStruct struct = null;
    List<SjasmStruct> structs = new ArrayList<>();
    
    List<String> modules = new ArrayList<>();
    
    boolean allowReusableLabels = true;
    HashMap<String, Integer> reusableLabelCounts = new HashMap<>();

    List<Integer> currentPages = new ArrayList<>();
    HashMap<String,Integer> symbolPage = new HashMap<>();
    

    // Some lines do not make sense in standard zilog assembler, and MDL removes them,
    // but if we want to generate assembler targetting this dialect, we need those lines.
    List<CodeStatement> linesToKeepIfGeneratingDialectAsm = new ArrayList<>(); 
    List<CodeStatement> auxiliaryStatementsToRemoveIfGeneratingDialectasm = new ArrayList<>();
    
    
    private SourceConstant getLastAbsoluteLabel(CodeStatement s) 
    {
        while(s != null) {
            // sjasm considers any label as an absolute label, even if it's associated with an equ,
            // so, no need to check if s.label.isLabel() (as in asMSX):
            if (s.label != null &&
                !s.label.originalName.startsWith(".") &&
                !config.tokenizer.isInteger(s.label.originalName)) {
                return s.label;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
    
    
    public final boolean addFakeInstruction(String in, String out)
    {
        String data[] = {in,"1","ff ff","2", "","","","", "","","","", "false"};
        CPUOpSpec fakeSpec = config.opSpecParser.parseOpSpecLine(data, config);
        if (fakeSpec == null) {
            config.error("cannot parse fake instruction " + in);
            return false;
        } 

        fakeSpec.fakeInstructionEquivalent = new ArrayList<>();
        for(String line:out.split("\n")) {
            fakeSpec.fakeInstructionEquivalent.add(config.tokenizer.tokenize(line));
        }
        
        config.opParser.addOpSpec(fakeSpec);        
        return true;
    }
    
    
    public boolean endStructDefinition(SourceLine sl, SourceFile source, CodeBase code)
    {
        // Transform the struct into equ definitions with local labels:
        int offset = 0;
        int start = source.getStatements().indexOf(struct.start) + 1;
        for (int i = start; i < source.getStatements().size(); i++) {
            CodeStatement s2 = source.getStatements().get(i);
            int offset_prev = offset;
            switch (s2.type) {
                case CodeStatement.STATEMENT_NONE:
                    break;
                case CodeStatement.STATEMENT_DATA_BYTES:
                case CodeStatement.STATEMENT_DATA_WORDS:
                case CodeStatement.STATEMENT_DATA_DOUBLE_WORDS:
                {
                    if (s2.data.size() != 1) {
                        config.error("No default value for field in struct (unsupported) in " + s2.sl);
                        return false;
                    }
                    int size = s2.sizeInBytes(code, true, true, true);
                    offset += size;
                    if (s2.label != null) {
                        struct.rawAttributeNames.add(s2.label.originalName);
                        struct.attributeNames.add(s2.label.name);
                        // update the original name of the struct field:
                        s2.label.originalName = s2.label.name;
                    } else {
                        struct.rawAttributeNames.add(null);
                        struct.attributeNames.add(null);
                    }
                    struct.attributeCodeStatementTypes.add(s2.type);
                    struct.attributeDefaults.add(s2.data.get(0));
                    struct.attributeDefiningStatement.add(s2);
                    break;
                }
                case CodeStatement.STATEMENT_DEFINE_SPACE:
                {
                    Integer size = s2.space.evaluateToInteger(s2, code, true);
                    if (size == null) {
                        config.error("Cannot evaluate " + s2.space + " to an integer in " + s2.sl);
                        return false;
                    }
                    if (s2.label != null) {
                        struct.rawAttributeNames.add(s2.label.originalName);
                        struct.attributeNames.add(s2.label.name);
                        // update the original name of the struct field:
                        s2.label.originalName = s2.label.name;
                    } else {
                        struct.rawAttributeNames.add(null);
                        struct.attributeNames.add(null);
                    }
                    struct.attributeCodeStatementTypes.add(s2.type);
                    struct.attributeDefaults.add(s2.space_value);
                    struct.attributeDefiningStatement.add(s2);
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
        struct.start.label.exp = Expression.constantExpression(offset, config);
        structs.add(struct);
        config.lineParser.keywordsHintingALabel.add(struct.name);
        return true;
    }
        
    
    public boolean parseLineStruct(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) {
    
        // struct definitions:
        for(SjasmStruct st:structs) {
            if (tokens.get(0).equals(st.name)) {
                tokens.remove(0);
                
                // it is a struct definition:
                boolean done = false;
                List<Expression> data = new ArrayList<>();
                
                if (tokens.isEmpty() || tokens.get(0).equals(":") || config.tokenizer.isSingleLineComment(tokens.get(0))) done = true;
                
                while (!done) {
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp == null) {
                        config.error("Cannot parse struct instantiation in " + sl);
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
                
                // fill the rest with defaults:
                for(int i = data.size();i<st.attributeNames.size();i++) {
                    data.add(st.attributeDefaults.get(i));
                }
                
                if (data.size() > st.attributeNames.size()) {
                    config.error("Struct instantiation has too many fields ("+data.size()+" vs the expected "+st.attributeNames.size()+") in " + sl);
                    return false;                    
                }
                
                for(int i = 0;i<data.size();i++) {
                    CodeStatement s2;
                    switch(st.attributeCodeStatementTypes.get(i)) {
                        case CodeStatement.STATEMENT_DATA_BYTES:
                        case CodeStatement.STATEMENT_DATA_WORDS:
                        case CodeStatement.STATEMENT_DATA_DOUBLE_WORDS:
                            s2 = new CodeStatement(st.attributeCodeStatementTypes.get(i), sl, source, config);
                            s2.data = new ArrayList<>();
                            s2.data.add(data.get(i));
                            l.add(s2);
                            break;
                        case CodeStatement.STATEMENT_DEFINE_SPACE:
                            s2 = new CodeStatement(CodeStatement.STATEMENT_DEFINE_SPACE, sl, source, config);
                            s2.space = st.attributeDefiningStatement.get(i).space;
                            s2.space_value = st.attributeDefiningStatement.get(i).space_value;
                            l.add(s2);
                            break;
                        default:
                            config.error("Field " + st.attributeNames.get(i) + " of struct " + st.name + " has an unsupported type ("+st.attributeCodeStatementTypes.get(i)+") in " + sl);
                            return false;
                    }
                    if (s.label != null) {
                        SourceConstant c = new SourceConstant(s.label.name + "." + st.rawAttributeNames.get(i),
                                s.label.name + "." + st.rawAttributeNames.get(i), 
                                Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s2, code, config), s2, config);
                        s2.label = c;
                        int res = code.addSymbol(c.name, c);
                        if (res == -1) return false;
                        if (res == 0) s.redefinedLabel = true; 
                    }
                }
                return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
            }
        }
        
        return false;
    }
    
    
    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement previous) {
        SourceConstant lastAbsoluteLabel = null;
        if (name.startsWith("@")) {
            name = name.substring(1);
        } else if (name.startsWith(".")) {
            lastAbsoluteLabel = getLastAbsoluteLabel(previous);
            if (lastAbsoluteLabel != null) {
                name = lastAbsoluteLabel.name + name;
            } else {
                name = config.lineParser.getLabelPrefix() + name;
            }
        } else if (allowReusableLabels && config.tokenizer.isInteger(name)) {
            // it's a reusable label:
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            reusableLabelCounts.put(name, count+1);
            name = config.lineParser.getLabelPrefix() + "_sjasm_reusable_" + name + "_" + count;
        } else {
            name = config.lineParser.getLabelPrefix() + name;
        }
        
        if (forbiddenLabelNames.contains(name.toLowerCase())) return null;
        
        if (currentPages.isEmpty()) {
//            config.warn("Defining a symbol, without any page selected, defaulting to 0");
            symbolPage.put(name, 0);
        } else {
            // set to the first candidate page for not (this will be overwritten later,
            // when blocks are assigned to pages):
            symbolPage.put(name, currentPages.get(0));
        }
        
        return Pair.of(name, lastAbsoluteLabel);
    }
    
    
    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement previous) {
        SourceConstant lastAbsoluteLabel = null;
        if (name.startsWith("@")) {
            return Pair.of(name.substring(1), null);
        } else if (name.startsWith(".")) {
            lastAbsoluteLabel = getLastAbsoluteLabel(previous);
            if (lastAbsoluteLabel != null) {
                return Pair.of(lastAbsoluteLabel.name + name, lastAbsoluteLabel);
            } else {
                return Pair.of(name, null);
            }
        } else if (allowReusableLabels && (name.endsWith("f") || name.endsWith("F")) && config.tokenizer.isInteger(name.substring(0, name.length()-1))) {
            // it'startStatement a reusable label:
            name = name.substring(0, name.length()-1);
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            return Pair.of("_sjasm_reusable_" + name + "_" + count, null);
        } else if (allowReusableLabels && (name.endsWith("b") || name.endsWith("B")) && config.tokenizer.isInteger(name.substring(0, name.length()-1))) {
            // it'startStatement a reusable label:
            name = name.substring(0, name.length()-1);
            int count = reusableLabelCounts.get(name);
            return Pair.of("_sjasm_reusable_" + name + "_" + (count-1), null);
        } else {            
            return Pair.of(name, null);
        }
    }
    
    
    public boolean parseAbyte(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("abyte")) {
            tokens.remove(0);
            
            Expression offset = config.expressionParser.parse(tokens, s, previous, code);
            if (offset == null) {
                config.error("Could not parse offset in " + sl);
                return false;
            }
            
            // parse it as a "db", and then add 1 to each expression (if any is a string or a list, add to each element):
            if (!config.lineParser.parseData(tokens, config.lineParser.KEYWORD_DB, l, sl, s, previous, source, code)) {
                return false;
            }
            
            List<Expression> newData = new ArrayList<>();
            for(Expression exp:s.data) {
                if (exp.evaluatesToNumericConstant()) {
                    newData.add(Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                            Expression.parenthesisExpressionIfNotConstant(exp, "(", config), 
                            Expression.parenthesisExpressionIfNotConstant(offset, "(", config), config));
                } else if (exp.evaluatesToStringConstant()) {
                    String str = exp.evaluateToString(s, code, true);
                    if (str == null) {
                        config.error("Unsuported form of abyte (could not evaluate the string) in " + sl);
                        return false;
                    }
                    for(int i = 0;i<str.length();i++) {
                        newData.add(Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                                Expression.constantExpression(str.substring(i, i +1), config), 
                                Expression.parenthesisExpressionIfNotConstant(offset, "(", config), config));
                    }
                } else {
                    config.error("Unsuported form of abyte in " + sl);
                    return false;
                }
            }
            s.data = newData;
            
            return true;
        }
        return false;        
    }
        
}

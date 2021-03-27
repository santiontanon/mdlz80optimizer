/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;

/**
 *
 * @author santi
 * 
 * Macro80 documentation: http://www.msxarchive.nl/pub/msx/programming/asm/m80l80.txt
 * 
 */
public class Macro80Dialect implements Dialect {

    MDLConfig config;

    // Some lines do not make sense in standard zilog assembler, and MDL removes them,
    // but if we want to generate assembler targetting this dialect, we need those lines.
    List<SourceLine> linesToKeepIfGeneratingDialectAsm = new ArrayList<>(); 
    List<CodeStatement> auxiliaryStatementsToRemoveIfGeneratingDialectasm = new ArrayList<>();
    
    Expression programStartAddress = null;
    
    List<String> globalLabels = new ArrayList<>();
    
    
    public Macro80Dialect(MDLConfig a_config)
    {
        config = a_config;
        
        config.caseSensitiveSymbols = false;
        config.convertSymbolstoUpperCase = true;
        config.hexStyle = MDLConfig.HEX_STYLE_H_CAPS;

        config.lineParser.allowIncludesWithoutQuotes = true;
        config.tokenizer.allowQuestionMarksInSymbols = true;
        
        config.expressionParser.opSynonyms.clear();
        config.expressionParser.OP_LSHIFT = "shl";
        config.expressionParser.OP_RSHIFT = "shr";
        config.expressionParser.OP_BIT_NEGATION = "not";
        config.expressionParser.OP_BIT_OR = "or";
        config.expressionParser.OP_BIT_AND = "and";
        config.expressionParser.OP_EQUAL = "eq";
        config.expressionParser.OP_LOWERTHAN = "lt";
        config.expressionParser.OP_GREATERTHAN = "gt";
        config.expressionParser.OP_LEQTHAN = "le";
        config.expressionParser.OP_GEQTHAN = "ge";
        config.expressionParser.OP_DIFF = "ne";
        config.expressionParser.OP_MOD = "mod";
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "high", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "low", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);
        config.expressionParser.doubleHashToMarkExternalSymbols = true;
        
        config.lineParser.keywordsHintingANonScopedLabel.add("set");
        config.lineParser.keywordsHintingANonScopedLabel.add("defl");        
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defm", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("::", config.lineParser.KEYWORD_COLON);
        
        config.preProcessor.macroSynonyms.put("cond", config.preProcessor.MACRO_IF);
        config.preProcessor.macroSynonyms.put("endc", config.preProcessor.MACRO_ENDIF);
        
        config.preProcessor.dialectMacros.put("irp", "endm");
        
    }
    

    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement s) {
        if (name.equalsIgnoreCase("not") ||
            name.equalsIgnoreCase("low") ||
            name.equalsIgnoreCase("high") ||
            name.equalsIgnoreCase("mod")) {
            return null;
        }        
        return Pair.of(config.lineParser.getLabelPrefix() + name, null);
    }
    

    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement s) {
        if (name.equalsIgnoreCase("not") ||
            name.equalsIgnoreCase("low") ||
            name.equalsIgnoreCase("high") ||
            name.equalsIgnoreCase("mod")) {
            return null;
        }        
        return Pair.of(name, null);
    }
 
    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code)
    {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("name")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("title")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("subttl")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".8080")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".z80")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("common")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("aseg")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("cseg")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("dseg")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("dc")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("irp")) return true;
        if (tokens.size()>=2 && 
            (tokens.get(0).equalsIgnoreCase("set") || tokens.get(0).equalsIgnoreCase("defl")) && 
            label != null) {
            // only if there is a single expression afterwwards:
            List<String> tmpTokens = new ArrayList<>();
            for(int i = 1;i<tokens.size();i++) {
                tmpTokens.add(tokens.get(i));
            }
            Expression tmp = config.expressionParser.parse(tmpTokens, null, null, code);
            if (tmp == null) return false;
            if (!tmpTokens.isEmpty() && !config.tokenizer.isSingleLineComment(tmpTokens.get(0))) {
                return false;
            }
            return true;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".list")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".xlist")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("page")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("*") && 
                                tokens.get(1).equalsIgnoreCase("eject")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".comment")) return true;
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase("ext") ||
                                 tokens.get(0).equalsIgnoreCase("extrn") ||
                                 tokens.get(0).equalsIgnoreCase("external"))) return true;
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase("entry") ||
                                 tokens.get(0).equalsIgnoreCase("public") ||
                                 tokens.get(0).equalsIgnoreCase("global"))) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printx")) return true;
        
        return false;
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l,
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("name")) {
            // ignore the rest of the line:
            tokens.clear();
            linesToKeepIfGeneratingDialectAsm.add(sl);
            
            return true;
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("title")) {
            // ignore the rest of the line:
            tokens.clear();
            linesToKeepIfGeneratingDialectAsm.add(sl);
            
            return true;
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("subttl")) {
            // ignore the rest of the line:
            tokens.clear();
            linesToKeepIfGeneratingDialectAsm.add(sl);
            
            return true;
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(".8080")) {
            config.error(".8080 mode not supported!");
            return false;
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(".z80")) {
            tokens.remove(0);
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("common")) {
            tokens.remove(0);
            String blockName = "";
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0)) &&
                    !config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                blockName += tokens.remove(0);
            }
            // for now, ignore ...
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("aseg")) {
            tokens.remove(0);
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("cseg")) {
            tokens.remove(0);
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("dseg")) {
            tokens.remove(0);
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("dc")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            if (!exp.evaluatesToStringConstant()) {
                config.error("parameter of 'DC' does not evaluate to a string!");
                return false;
            }
            String str = exp.evaluateToString(s, code, true);
            if (str == null) { 
                config.error("Cannot evaluate expression: " + exp + " in " + sl);
                return false;
            }
            s.type = CodeStatement.STATEMENT_DATA_BYTES;
            s.data = new ArrayList();
            if (str.length()>1) {
                Expression exp1 = Expression.constantExpression(str.substring(0, str.length()-1), config);
                s.data.add(exp1);
            }
            Expression exp2 = Expression.operatorExpression(Expression.EXPRESSION_BITOR, 
                    Expression.constantExpression(str.substring(str.length()-1), config),
                    Expression.constantExpression(0x80, Expression.RENDER_AS_8BITHEX, config), config);
            s.data.add(exp2);

            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("irp")) {
            tokens.remove(0);
            List<Expression> args = new ArrayList<>();
            Expression arg1 = config.expressionParser.parse(tokens, s, previous, code);
            args.add(arg1);
            if (arg1 == null || tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase(",")) {
                config.error("Cannot parse line " + sl);
                return false;
            }
            tokens.remove(0);
            if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("<")) {
                config.error("Cannot parse line " + sl);
                return false;
            }
            tokens.remove(0);
            while(!tokens.isEmpty() && !tokens.get(0).equalsIgnoreCase(">")) {
                Expression arg = config.expressionParser.parse(tokens, s, previous, code);
                if (arg == null || tokens.isEmpty()) {
                    config.error("Cannot parse line " + sl);
                    return false;
                }
                args.add(arg);
                if (tokens.get(0).equalsIgnoreCase(",")) {
                    tokens.remove(0);
                }
            }
            if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase(">")) {
                config.error("Cannot parse line " + sl);
                return false;
            }
            tokens.remove(0);
            
            s.macroCallName = "irp";
            s.macroCallArguments = args;
            s.type = CodeStatement.STATEMENT_MACROCALL;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        if (tokens.size() >= 2 && (tokens.get(0).equalsIgnoreCase("set") || tokens.get(0).equalsIgnoreCase("defl")) && 
            s.label != null) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode in " + sl);
                return false;
            }
            
            s.label.clearCache();
            s.label.resolveEagerly = true;            
            
            tokens.remove(0);
            if (!config.lineParser.parseEqu(tokens, l, sl, s, previous, source, code)) return false;
            Integer value = s.label.exp.evaluateToInteger(s, code, false, previous);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return false;
            }
            Expression exp = Expression.constantExpression(value, config);
            s.label.exp = exp;
            s.label.clearCache();
            
            // these variables should not be part of the source code:
            l.clear();
            return true;
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(".list")) {
            tokens.remove(0);
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(".xlist")) {
            tokens.remove(0);
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("end")) {
            tokens.remove(0);
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
                programStartAddress = exp;
            }
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("page")) {
            // ignore ...
            tokens.remove(0);
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
            }
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("*") && 
                                tokens.get(1).equalsIgnoreCase("eject")) {
            // ignore ...
            tokens.remove(0);
            tokens.remove(0);
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
            }            
            linesToKeepIfGeneratingDialectAsm.add(sl);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".comment")) {
            tokens.remove(0);
            // convert into a multiline comment
            String token = tokens.remove(0);
            String delimiter = token.charAt(0) + "";
            if (token.length() > 1) {
                tokens.add(0, token.substring(1));
            }
            tokens.add(0, delimiter);
            
            config.tokenizer.oneTimemultilineCommentStartTokens.add(delimiter);
            config.tokenizer.oneTimemultilineCommentEndTokens.add(delimiter);
            
            if (!config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code)) {
                return false;
            }            
            return true;
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase("ext") ||
                                 tokens.get(0).equalsIgnoreCase("extrn") ||
                                 tokens.get(0).equalsIgnoreCase("external"))) {
            tokens.remove(0);
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0)) &&
                    !config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                String token = tokens.remove(0);
                if (!config.tokenizer.isSymbol(token)) {
                    config.error("Expected symbol name in " + sl);
                    return false;
                }
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                    tokens.remove(0);
                }
            }
            
            linesToKeepIfGeneratingDialectAsm.add(sl);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase("entry") ||
                                 tokens.get(0).equalsIgnoreCase("public") ||
                                 tokens.get(0).equalsIgnoreCase("global"))) {
            tokens.remove(0);
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0)) &&
                    !config.tokenizer.isMultiLineCommentStart(tokens.get(0))) {
                String token = tokens.remove(0);
                if (!config.tokenizer.isSymbol(token)) {
                    config.error("Expected symbol name in " + sl);
                    return false;
                }
                globalLabels.add(token);
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                    tokens.remove(0);
                }
            }
            
            linesToKeepIfGeneratingDialectAsm.add(sl);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printx")) {
            tokens.remove(0);
            String delimiter = tokens.remove(0);
            String text = "";
            while(!tokens.isEmpty() && !tokens.get(0).equals(delimiter)) {
                text += tokens.remove(0) + " ";
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(delimiter)) {
                tokens.remove(0);
            }
            
            config.info(text);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        
        
        return false;
    }
    
    
    @Override
    public MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, CodeStatement macroCall, CodeBase code)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        MacroExpansion me = new MacroExpansion(macro, macroCall, lines2);
        
        if (macro.name.equals("irp")) {
            if (args.isEmpty()) return null;
            if (args.get(0).type != Expression.EXPRESSION_SYMBOL) {
                config.error("First parameter to IRP should be a variable name");
                return null;
            }
            String variableName = args.get(0).symbolName;            
            String scope;
            if (macroCall.label != null) {
                scope = macroCall.label.name;
            } else {
                scope = config.preProcessor.nextMacroExpansionContextName(macroCall.labelPrefix);
            }
            for(int i = 1;i<args.size();i++) {
                List<SourceLine> linesTmp = new ArrayList<>();
                for(SourceLine sl:macro.lines) {
                    // we create new instances, as we will modify them:
                    linesTmp.add(new SourceLine(sl.line, sl.source, sl.lineNumber));
                }
                macro.scopeMacroExpansionLines(scope+"."+i, linesTmp, code, config);
                for(SourceLine sl:linesTmp) {
                    String line2 = sl.line;
                    line2 = line2.replace(variableName, args.get(i).toString());
                    lines2.add(new SourceLine(line2, sl.source, sl.lineNumber));
                }
            }
            return me;
        } else {
            return null;
        }
    }
    
    
    @Override
    public Integer evaluateExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code, boolean silent)
    {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            Integer value = args.get(0).evaluateToInteger(s, code, silent);
            if (value == null) return null;
            return (value >> 8)&0xff;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            Integer value = args.get(0).evaluateToInteger(s, code, silent);
            if (value == null) return null;
            return value&0xff;
        }
        return null;
    }

    
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code) {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            Expression exp = Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, Expression.RENDER_AS_16BITHEX, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
            return exp;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, Expression.RENDER_AS_16BITHEX, config), config);
        }
        return null;
    }
        
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code, boolean useOriginalNames, Path rootPath) {
        if (linesToKeepIfGeneratingDialectAsm.contains(s.sl)) {
            return s.sl.line;
        }

        if (auxiliaryStatementsToRemoveIfGeneratingDialectasm.contains(s)) {
            return "";
        }
        
        return s.toStringUsingRootPath(rootPath, useOriginalNames, true, code);
    }    
           
    
    @Override
    public boolean postParseActions(CodeBase code)
    {
        // look for global labels:
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.label != null && s.label.colonTokenUsedInDefinition != null &&
                     s.label.colonTokenUsedInDefinition.equals("::")) {
                    if (!globalLabels.contains(s.label.originalName)) {
                        globalLabels.add(s.label.originalName);
                    }
                }
            }
        }
        
        return true;
    }    
    
    
    @Override
    public boolean labelIsExported(SourceConstant label)
    {
        return globalLabels.contains(label.originalName);
    }    
}

/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.util.HashMap;
import org.apache.commons.lang3.tuple.Pair;
import parser.MacroExpansion;
import parser.PreProcessor;
import parser.SourceLine;
import parser.SourceMacro;

/**
 *
 * @author santi
 */
public class GlassDialect implements Dialect {
    public static class SectionRecord {
        String name;
        CodeStatement dsStatement = null;
        List<SectionPortionRecord> portions = new ArrayList<>();
        
        public SectionRecord(String a_name) {
            name = a_name;
        }
    }
    
    public static class SectionPortionRecord {
        String name;
        CodeStatement start = null;
        CodeStatement end = null;
        
        public SectionPortionRecord(String a_name) {
            name = a_name;
        }
    }
    
    
    MDLConfig config;
    
    // Although this is not documented, it seems you can have the same "section XXX" command multiple times
    // in the same codebase, and the address counters just continue from the last time. So, we need to keep
    // track of how many times each section has appeared:
    HashMap<String, SectionRecord> sections = new HashMap<>();
    List<SectionPortionRecord> sectionStack = new ArrayList<>();
        
    // We keep track, to give a warning at the end, since Section is not fully supported yet if MDL is asked to generate output assembler
    boolean usedSectionKeyword = false;
    
    public GlassDialect(MDLConfig a_config)
    {
        config = a_config;
        
        config.considerLinesEndingInCommaAsUnfinished = true;

        config.eagerMacroEvaluation = false;  // Glass expects lazy evaluation of macros
        
        config.preProcessor.dialectMacros.put("irp", "endm");
        config.preProcessor.addScopeLabelsToRept = true;
        
        // recognized escape sequences by Glass:
        config.tokenizer.stringEscapeSequences.put("0", "\u0000");
        config.tokenizer.stringEscapeSequences.put("a", "\u0007");
        config.tokenizer.stringEscapeSequences.put("t", "\t");
        config.tokenizer.stringEscapeSequences.put("n", "\n");
        config.tokenizer.stringEscapeSequences.put("f", "\f");
        config.tokenizer.stringEscapeSequences.put("r", "\r");
        config.tokenizer.stringEscapeSequences.put("e", "\u0027");
        config.tokenizer.stringEscapeSequences.put("\"", "\"");
        config.tokenizer.stringEscapeSequences.put("'", "'");
        config.tokenizer.stringEscapeSequences.put("\\", "\\");
    }


    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("error")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("warning")) return true;
        
        return false;
    }


    @Override
    public Pair<String, SourceConstant> newSymbolName(String name, Expression value, CodeStatement previous) {
        if (name.equalsIgnoreCase("org") ||
            name.equalsIgnoreCase("db") ||
            name.equalsIgnoreCase("dw") ||
            name.equalsIgnoreCase("dd") ||
            name.equalsIgnoreCase("ds") ||
            name.equalsIgnoreCase("macro") ||
            name.equalsIgnoreCase("endm") ||
            name.equalsIgnoreCase("rept") ||
            name.equalsIgnoreCase("if") ||
            name.equalsIgnoreCase("else") ||
            name.equalsIgnoreCase("endif")) {
            return null;
        }
        return Pair.of(config.lineParser.getLabelPrefix() + name, null);
    }


    @Override
    public Pair<String, SourceConstant> symbolName(String name, CodeStatement previous)
    {
        return Pair.of(name, null);
    }

    
    String getSectionName(String baseName, CodeBase code)
    {
        SourceConstant sc = code.getSymbol(baseName);
        if (sc == null) return baseName;
        while(sc.exp != null && sc.exp.type == Expression.EXPRESSION_SYMBOL && !sc.exp.symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
            SourceConstant sc2 = code.getSymbol(sc.exp.symbolName);
            if (sc2 == null) break;
            sc = sc2;
        }
        return sc.name;
    }
    

    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l,
            SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) {
            usedSectionKeyword = true;
            
            tokens.remove(0);

            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl);
                return false;
            }
            if (exp.type != Expression.EXPRESSION_SYMBOL) {
                config.error("Invalid section name in " + sl);
                return false;
            }
            String sectionName = getSectionName(exp.symbolName, code);
            
            // Add a section record entry:
            SectionPortionRecord spr = new SectionPortionRecord(sectionName);
            spr.start = s;
            sectionStack.add(0,spr);
            SectionRecord sr = sections.get(spr.name);
            if (sr == null) {
                sr = new SectionRecord(spr.name);
                sections.put(spr.name, sr);
            }
            sr.portions.add(spr);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) {
            if (!sectionStack.isEmpty()) {
                tokens.remove(0);
                
                SectionPortionRecord spr = sectionStack.remove(0);
                spr.end = s;
                
                return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
            } else {
                config.error("No section to terminate in " + sl);
                return false;
            }
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) {
            if (s.label == null) {
                config.error("Proc with no name in " + sl);
                return false;
            }
            tokens.remove(0);
            config.lineParser.pushLabelPrefix(s.label.name + ".");
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) {
            tokens.remove(0);
            config.lineParser.popLabelPrefix();
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("error")) {
            config.error(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("warning")) {
            config.warn(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        return false;
    }
    
    
    // Returns: <SourceLine, file_linenumber>
    Pair<SourceLine, Integer> getNextLine(List<SourceLine> lines, SourceFile f, int file_linenumber, List<String> tokens, PreProcessor preProcessor)
    {
        List<String> unfilteredTokens = new ArrayList<>();
        
        SourceLine sl = preProcessor.expandMacros();
        if (sl == null && !lines.isEmpty()) {
            sl = lines.remove(0); // ignore the line numbers here
            file_linenumber++;
        }
        if (sl == null) return null;
        
        config.tokenizer.tokenize(sl.line, unfilteredTokens);
        if (!unfilteredTokens.isEmpty() && unfilteredTokens.get(unfilteredTokens.size()-1).equals(",")) {
            // unfinished line, get the next one!
            List<String> tokens2 = new ArrayList<>();
            Pair<SourceLine, Integer> tmp = getNextLine(lines, f, file_linenumber, tokens2, preProcessor);
            if (tmp != null) {
                sl = new SourceLine(sl.line += "\n" + tmp.getLeft().line, sl.source, sl.lineNumber);
                unfilteredTokens.addAll(tokens2);
                file_linenumber = tmp.getRight();
            }
        }
        
        // Glass does not support multi-line comments, so, no need to handle that here as in SourceCodeParser.getNextLine
        tokens.addAll(unfilteredTokens);
        
        return Pair.of(sl, file_linenumber);
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
    public boolean performAnyPostParsingActions(CodeBase code)
    {
        // Assemble Macros:
        for(List<SourceMacro> l:config.preProcessor.macros.values()) {
            for(SourceMacro macro:l) {
                config.lineParser.clearPrefixStack();
                assembleMacro(macro, code);
                config.lineParser.clearPrefixStack();
            }
        }   
        
        return true;
    }
    
    
    @Override
    public boolean postParseActions(CodeBase code)
    {                        
        // Resolve sections:
        // Find the corresponding "ds" for each section:
        for(SectionRecord sr: sections.values()) {
            SourceConstant sc = code.getSymbol(sr.name);
            if (sc == null) {
                config.error("Cannot find 'ds' statement corresponding to section " + sr.name);
                return false;
            }
            if(!sc.isLabel()) {
                config.error("Cannot find 'ds' statement corresponding to section " + sr.name);
                return false;
            }
            sr.dsStatement = code.statementDefiningLabel(sc.name);
            if (sr.dsStatement == null || sr.dsStatement.type != CodeStatement.STATEMENT_DEFINE_SPACE) {
                config.error("Cannot find 'ds' statement corresponding to section " + sr.name);
                return false;
            }
            if (sr.dsStatement.label == null) {
                config.error("Section 'ds' statement does not have a label for section " + sr.name + "!");
                return false;                
            }
            
            // update the ds statement to have the label and the space in two separate statements, to insert all the section portions in between:
            CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_NONE, sr.dsStatement.sl, sr.dsStatement.source, config);
            s.labelPrefix = sr.dsStatement.labelPrefix;
            s.label = sr.dsStatement.label;
            s.label.definingStatement = s;
            sr.dsStatement.space = Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                    Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_SUM,
                                    Expression.symbolExpression(sr.dsStatement.label.name, s, code, config), 
                                    Expression.parenthesisExpression(sr.dsStatement.space, "(", config), config),
                            "(", config), 
                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), 
                    config);
            sr.dsStatement.label = null;
            sr.dsStatement.source.getStatements().add(sr.dsStatement.source.getStatements().indexOf(sr.dsStatement), s);
        }
        
        // Move the section blocks to their respective places:
        for(SectionRecord sr: sections.values()) {
            for(SectionPortionRecord spr: sr.portions) {
                SourceFile ds_source = sr.dsStatement.source;
                CodeStatement start = spr.start;
                CodeStatement end = spr.end;

                if (start.source != end.source) {
                    config.error("Section "+sr.name+" starts and ends in a different source file. This case is not yet supported!");
                    config.error("start is in " + start.sl);
                    config.error("end is in " + end.sl);
                    return false;
                }

                SourceFile source = start.source;
                int start_idx = source.getStatements().indexOf(start);
                int end_idx = source.getStatements().indexOf(end);
                if (start_idx > end_idx) {
                    config.error("inconsistent start and end of a portion of section " + sr.name + "!");
                    return false;                    
                }

                if (start_idx == -1 || end_idx == -1) {
                    config.error("Cannot find start/end statements of section " + sr.name + "!");
                    return false;
                }

                List<CodeStatement> l = new ArrayList<>();
                for(int j = 0;j<=(end_idx - start_idx);j++) {
                    CodeStatement s = source.getStatements().remove(start_idx);
                    s.source = ds_source;
                    l.add(s);
                }

                int insertion_idx = ds_source.getStatements().indexOf(sr.dsStatement);
                ds_source.getStatements().addAll(insertion_idx, l);
            }
        }
        
        code.resetAddresses();
            
        return true;
    }    
    
    
    public boolean assembleMacro(SourceMacro macro, CodeBase code)
    {        
        // Attempt to assemble the macro content at address 0, and define all the internal symbols as macroname.symbol:
        // To do that, I instantiate the macro with all the parameters that do not have defaults taking the value 0:
        // However, it is not always possible to do this, so, this is only attempted, and if it fails
        // no compilation happens:
        List<Expression> args = new ArrayList<>();
        for(int i=0;i<macro.argNames.size();i++) {
            if (macro.defaultValues.get(i) != null) {
                args.add(macro.defaultValues.get(i));
            } else {
                args.add(Expression.constantExpression(0, config));
            }
        }

        // Assemble the macro at address 0:
        boolean succeeded = true;
        SourceFile f = new SourceFile(macro.definingStatement.source.fileName + ":macro(" + macro.name+")", null, null, code, config);
        try {
            // supress error messages when attempting to assemble a macro, as it might fail:
            config.logger.silence();
            MacroExpansion expansion = macro.instantiate(args, macro.definingStatement, code, config);
            List<SourceLine> lines = expansion.lines;
            PreProcessor preProcessor = new PreProcessor(config.preProcessor);

            int lineNumber = macro.definingStatement.sl.lineNumber;
            while(true) {
                List<String> tokens = new ArrayList<>();
                Pair<SourceLine, Integer> tmp = getNextLine(lines, f, lineNumber, tokens, preProcessor);
                if (tmp == null) {
                    if (config.preProcessor.withinMacroDefinition()) {
                        // we fail to evaluateToInteger the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    } else {
                        config.debug("Glass: successfully assembled macro " + macro.name);
                    }
                    break;
                }
                lineNumber = tmp.getRight();
                SourceLine sl = tmp.getLeft();
                
                if (preProcessor.withinMacroDefinition()) {
                    List<CodeStatement> newStatements = preProcessor.parseMacroLine(tokens, sl, f, code, config);
                    if (newStatements == null) {
                        // we fail to evaluateToInteger the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    } else {
                        for(CodeStatement s:newStatements) {
                            f.addStatement(s);
                        }
                    }
                } else {
                    List<CodeStatement> l = config.lineParser.parse(config.tokenizer.tokenize(sl.line), 
                            sl, f, f.getStatements().size(), code, config);
                    if (l == null) {
                        // we fail to assemble the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    }
                    for(CodeStatement s:l) {
                        List<CodeStatement> l2 = preProcessor.handleStatement(sl, s, f, code, false);
                        if (l2 == null) {
                            f.addStatement(s);
                        } else {
                            for(CodeStatement s2:l2) {
                                f.addStatement(s2);
                            }
                        }
                    }
                }
            }
            
            // resolve local labels:
            for(CodeStatement s:f.getStatements()) {
                s.resolveLocalLabels(code);
            }
            
            config.codeBaseParser.expandAllMacros(f, code);
        } catch (Exception e) {
            // we fail to evaluateToInteger the macro, but it's ok, some times it can happen
            succeeded = false;
        }
        config.logger.resume();

        // this is a debug message, not a warning, as it can definitively happen if macros contain unresolved symbols:
        if (succeeded) {
            // Add all the new symbols to the source:
            for(CodeStatement s:f.getStatements()) {
                if (s.label != null &&
                    !s.label.name.startsWith(config.preProcessor.unnamedMacroPrefix)) {
                    Object value = s.label.getValue(code, true);
                    if (value != null && value instanceof Integer) {
                        CodeStatement label_s = new CodeStatement(CodeStatement.STATEMENT_CONSTANT, macro.definingStatement.sl, macro.definingStatement.source, config);
                        label_s.label = s.label;
                        label_s.label.exp = Expression.constantExpression((Integer)value, config);
                        SourceFile label_f = macro.definingStatement.source;
                        label_f.addStatement(0, label_s);
                    }
                }
            }                        
        } else {
            config.debug("Glass: failed to assemble macro " + macro.name);
        }

        return true;
    }    
}

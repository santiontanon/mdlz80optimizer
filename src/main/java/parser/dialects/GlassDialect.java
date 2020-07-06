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
import code.SourceStatement;
import java.util.HashMap;
import parser.MacroExpansion;
import parser.PreProcessor;
import parser.SourceLine;
import parser.SourceMacro;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class GlassDialect implements Dialect {
    MDLConfig config;
    List<String> sectionStack = new ArrayList<>();
    
    // Although this is not documented, it seems you can have the same "section XXX" command multiple times
    // in the same codebase, and the address counters just continue from the last time. So, we need to keep
    // track of how many times each section has appeared:
    HashMap<String,Integer> sectionAppearanceCounters = new HashMap<>();

    public GlassDialect(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public boolean recognizeIdiom(List<String> tokens)
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
    public String newSymbolName(String name, Expression value) {
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
        return name;
    }


    @Override
    public String symbolName(String name)
    {
        return name;
    }


    @Override
    public List<SourceStatement> parseLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) {
            // TODO(santi@): implement "section" with the same semantics as Glass. I am currently just
            // approximating it by replacing it with "org"
            tokens.remove(0);

            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return null;
            }
            if (exp.type != Expression.EXPRESSION_SYMBOL) {
                config.error("Invalid section name at " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return null;
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            sectionStack.add(0, exp.symbolName);
            
            int appearanceCounter = 1;
            if (sectionAppearanceCounters.containsKey(exp.symbolName)) {
                appearanceCounter = sectionAppearanceCounters.get(exp.symbolName);
            } else {
                sectionAppearanceCounters.put(exp.symbolName, appearanceCounter);
            }
            
            // Insert a helper statement, so we can restore the address counter after the section is complete:
            SourceStatement sectionHelper = new SourceStatement(SourceStatement.STATEMENT_CONSTANT, source, lineNumber, null);
            sectionHelper.label = new SourceConstant("__address_before_section_"+exp.symbolName+"_"+appearanceCounter+"_starts", null, 
                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, code, config), sectionHelper);
            code.addSymbol(sectionHelper.label.name, sectionHelper.label);
            source.addStatement(sectionHelper);

            if (config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source)) return l;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) {
            if (!sectionStack.isEmpty()) {
                tokens.remove(0);
                String sectionName = sectionStack.remove(0);
                int appearanceCounter = sectionAppearanceCounters.get(sectionName);
                
                // Insert a helper statement, so we can restore the address counter after the section is complete:
                SourceStatement sectionHelper = new SourceStatement(SourceStatement.STATEMENT_ORG, source, lineNumber, null);
                sectionHelper.org = Expression.symbolExpression("__address_before_section_"+sectionName+"_"+appearanceCounter+"_starts", code, config);
                source.addStatement(sectionHelper);
                sectionAppearanceCounters.put(sectionName, appearanceCounter+1);
                
                if (config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source)) return l;
            } else {
                config.error("No section to terminate at " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return null;
            }
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) {
            if (s.label == null) {
                config.error("Proc with no name at " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return null;
            }
            tokens.remove(0);
            config.lineParser.pushLabelPrefix(s.label.name + ".");
            if (config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source)) return l;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) {
            tokens.remove(0);
            config.lineParser.popLabelPrefix();
            if (config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source)) return l;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("error")) {
            config.error(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source)) return l;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("warning")) {
            config.warn(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source)) return l;
        }
        return null;
    }


    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code)
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
        try {
            // supress error messages when attempting to assemble a macro, as it might fail:
            config.logger.silence();
            MacroExpansion expansion = macro.instantiate(args, macro.definingStatement, code, config);
            List<SourceLine> lines = expansion.lines;
            PreProcessor preProcessor = new PreProcessor(config.preProcessor);

            SourceFile f = new SourceFile(macro.definingStatement.source.fileName + ":macro(" + macro.name+")", null, null, config);
            int lineNumber = macro.definingStatement.lineNumber;
            while(true) {
                SourceLine sl = preProcessor.expandMacros();
                String line = null;
                if (sl != null) line = sl.line; // ignore the line numbers here
                if (line == null && !lines.isEmpty()) {
                    line = lines.remove(0).line; // ignore the line numbers here
                    lineNumber++;
                }
                if (line == null) {
                    if (preProcessor.withinMacroDefinition()) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    } else {
                        config.debug("Glass: successfully assembled macro " + macro.name);
                    }
                    break;
                }

                if (preProcessor.withinMacroDefinition()) {
                    List<SourceStatement> newStatements = preProcessor.parseMacroLine(Tokenizer.tokenize(line), line, lineNumber, f, code, config);
                    if (newStatements == null) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    } else {
                        for(SourceStatement s:newStatements) {
                            f.addStatement(s);
                        }
                    }
                } else {
                    List<SourceStatement> l = config.lineParser.parse(Tokenizer.tokenize(line), 
                            line, lineNumber, f, code, config);
                    if (l == null) {
                        // we fail to assemble the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    }
                    for(SourceStatement s:l) {
                        if (!s.isEmpty()) {
                            if (!preProcessor.handleStatement(line, lineNumber, s, f, code, false)) {
                                f.addStatement(s);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // we fail to evaluate the macro, but it's ok, some times it can happen
            succeeded = false;
        }
        config.logger.resume();

        // this is a debug message, not a warning, as it can definitively happen if macros contain unresolved symbols:
        if (!succeeded) config.debug("Glass: failed to assemble macro " + macro.name);

        return true;
    }
    
    
    @Override
    public Integer evaluateExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code, boolean silent)
    {
        return null;
    }
    
    
    @Override
    public void performAnyFinalActions(CodeBase code)
    {
        
    }
}

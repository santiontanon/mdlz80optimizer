/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import java.util.ArrayList;
import java.util.List;


import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
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
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code)
    {
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("section")) {
            // TODO(santi@): implement "section" with the same semantics as Glass. I am currently just
            // approximating it by replacing it with "org"
            tokens.remove(0);

            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse line " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return false;
            }
            if (exp.type != Expression.EXPRESSION_SYMBOL) {
                config.error("Invalid section name at " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return false;
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = exp;
            sectionStack.add(0, exp.symbolName);

            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ends")) {
            if (!sectionStack.isEmpty()) {
                sectionStack.remove(0);
                return true;
            } else {
                config.error("No section to terminate at " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return false;
            }
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("proc")) {
            if (s.label == null) {
                config.error("Proc with no name at " + source.fileName + ", " +
                             lineNumber + ": " + line);
                return false;
            }
            config.lineParser.pushLabelPrefix(s.label.name + ".");
            return true;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) {
            config.lineParser.popLabelPrefix();
            return true;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("error")) {
            config.error(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("warning")) {
            config.warn(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        return false;
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
                    if (!preProcessor.parseMacroLine(Tokenizer.tokenize(line),
                                                     line, lineNumber, f, code, config)) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    }
                } else {
                    SourceStatement s = config.lineParser.parse(Tokenizer.tokenize(line),
                                                                line, lineNumber, f, code, config);
                    if (s == null) {
                        // we fail to assemble the macro, but it's ok, some times it can happen
                        succeeded = false;
                        break;
                    }
                    if (!s.isEmpty()) {
                        if (!preProcessor.handleStatement(line, lineNumber, s, f, code, false)) {
                            f.addStatement(s);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // we fail to evaluate the macro, but it's ok, some times it can happen
            succeeded = false;
        }
        config.logger.resume();
        
        if (!succeeded) config.debug("Glass: failed to assemble macro " + macro.name);
        
        return true;
    }

}

/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.idioms;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;
import parser.PreProcessor;
import parser.SourceMacro;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class Glass implements Idiom {
    MDLConfig config;
    List<String> sectionStack = new ArrayList<>();
    
    public Glass(MDLConfig a_config)
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
    public boolean parseLine(List<String> tokens,
            String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code) throws Exception
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
            config.lineParser.pushLabelPrefix(s.label.name);
            return true;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("endp")) {
            config.lineParser.popLabelPrefix();
            return true;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("error")) {
            if (tokens.size()>=2) {
                config.error(tokens.get(1));
            }
            return false;
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("warning")) {
            if (tokens.size()>=2) {
                config.warn(tokens.get(1));
            }
            return true;
        }
        return false;
    }
    
    
    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code) throws Exception
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
                args.add(Expression.constantExpression(0));
            }
        }
        List<String> lines = macro.instantiate(args, code, config);
        PreProcessor preProcessor = new PreProcessor(config.preProcessor);
        
        // Assemble the macro at address 0:
        config.lineParser.pushLabelPrefix(macro.name + ".");
        // supress error messages when attempting to assemble a macro, as it might fail:
        config.logger.silence();
        try {
            SourceFile f = new SourceFile(macro.definingStatement.source.fileName + ":macro(" + macro.name+")", null, null, config);
            int lineNumber = macro.definingStatement.lineNumber;
            while(true) {
                String line = preProcessor.expandMacros();
                if (line == null && !lines.isEmpty()) {
                    line = lines.remove(0);
                    lineNumber++;                
                }
                if (line == null) {
                    if (preProcessor.withinMacroDefinition()) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
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
                        break;
                    }
                } else {
                    SourceStatement s = config.lineParser.parse(Tokenizer.tokenize(line), 
                                                                line, lineNumber, f, code, config);
                    if (s == null) {
                        // we fail to evaluate the macro, but it's ok, some times it can happen
                        break;
                    }
                    if (!s.isEmpty()) {
                        if (!preProcessor.handleStatement(line, lineNumber, s, f, code)) {
                            f.addStatement(s);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // we fail to evaluate the macro, but it's ok, some times it can happen
        }
        
        config.lineParser.popLabelPrefix();
        config.logger.resume();
        return true;        
    }
    
}

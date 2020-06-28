/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceMacro;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class PreProcessor {
    MDLConfig config;

    // current Macro we are parsing (should be null at the end of parsing a file):
    List<SourceMacro> currentMacroStack = new ArrayList<>();
    List<List<String>> macroExpansions = new ArrayList<>();


    public PreProcessor(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    public boolean withinMacroDefinition()
    {
        return !currentMacroStack.isEmpty();
    }
    
    
    public boolean isMacro(String name, CodeBase code)
    {
        if (code.getMacro(name) != null) return true;
        if (name.equalsIgnoreCase(SourceMacro.MACRO_REPT)) return true;
        if (name.equalsIgnoreCase(SourceMacro.MACRO_IF)) return true;
        return false;
    }    
    
    
    public String expandMacros()
    {
        while(!macroExpansions.isEmpty() && macroExpansions.get(0).isEmpty()) {
            macroExpansions.remove(0);
        }
        if (!macroExpansions.isEmpty()) {
            return macroExpansions.get(0).remove(0);
        } else {
            return null;
        }
    }
    
    
    public boolean parseMacroLine(String line, int lineNumber, SourceFile f, CodeBase code, MDLConfig config) throws Exception
    {
        List<String> tokens = Tokenizer.tokenize(line);
        SourceMacro m = currentMacroStack.get(0);
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDM)) {
            if (m.name.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                List<String> lines = m.instantiate(null, config);
                if (lines == null) return false;
                macroExpansions.add(lines);
                currentMacroStack.remove(0);
            } else if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                m.addLine(line);
            } else {
                code.addMacro(m);
                currentMacroStack.remove(0);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDIF)) {
            if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                List<String> lines = m.instantiate(null, config);
                if (lines == null) return false;
                macroExpansions.add(lines);
                currentMacroStack.remove(0);
            } else {
                m.addLine(line);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ELSE)) {
            m.insideElse = true;
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_IF)) {
            // nested if:
            tokens.remove(0);
            SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, f, lineNumber, code.getAddress());
            s.source = f;
            s.macroCallName = SourceMacro.MACRO_IF;
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Missing condition in " + f.fileName + ", " + 
                             lineNumber + ": " + line);
                return false;
            }
            s.macroCallArguments = new ArrayList<>();
            s.macroCallArguments.add(exp);
            if (!config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, f)) return false;
            return handleStatement(line, lineNumber, s, f, code);            
        } else {
            m.addLine(line);
        }     
        return true;
    }
    
    
    public boolean handleStatement(String line, int lineNumber, 
            SourceStatement s, SourceFile source, CodeBase code)
    {
        if (s.type == SourceStatement.STATEMENT_MACROCALL) {
            if (s.macroCallName.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_IF);
                Integer result = s.macroCallArguments.get(0).evaluate(s, code, false);
                if (result == null) {
                    config.error("Could not evaluate condition in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
                m.ifCondition = result;
                currentMacroStack.add(0, m);
                return true;
            } else if (s.macroCallName.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_REPT);
                Integer repetitions = s.macroCallArguments.get(0).evaluate(s, code, false);
                if (repetitions == null) {
                    config.error("Could not evaluate number of repetitions in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
                m.reptNRepetitions = repetitions;
                currentMacroStack.add(0, m);   
                return true;
            } else {
                SourceMacro m = code.getMacro(s.macroCallName);    
                List<String> expandedMacro = m.instantiate(s.macroCallArguments, config);
                if (expandedMacro == null) {
                    config.error("Problem instantiating macro "+s.macroCallName+" in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;            
                }
                macroExpansions.add(expandedMacro);
                return true;
            }
        } else {
            if (s.type == SourceStatement.STATEMENT_MACRO) {
                currentMacroStack.add(0, new SourceMacro(s.label.name, s.macroDefinitionArgs));
                return true;
            }
            return false;
        }
    }
        
}

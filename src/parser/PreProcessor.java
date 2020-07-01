/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 *
 * @author santi
 */
public class PreProcessor {
    MDLConfig config;

    // current Macro we are parsing (should be null at the end of parsing a file):
    List<String> currentMacroNameStack = new ArrayList<>();
    SourceMacro currentMacro = null;
    List<List<String>> macroExpansions = new ArrayList<>();

    LinkedHashMap<String, SourceMacro> macros = new LinkedHashMap<>();
    

    public PreProcessor(MDLConfig a_config)
    {
        config = a_config;
    }


    // instantiate a new preProcessor with a clean state, but linked to the previous one (sharing "macros")
    public PreProcessor(PreProcessor pp)
    {
        config = pp.config;
        macros = pp.macros;
    }
    
    
    public boolean withinMacroDefinition()
    {
        return currentMacro != null;
    }
    
    
    public boolean isMacro(String name, CodeBase code)
    {
        if (getMacro(name) != null) return true;
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
    
    
    public boolean parseMacroLine(List<String> tokens, String line, int lineNumber, SourceFile f, CodeBase code, MDLConfig config) throws Exception
    {
        SourceMacro m = currentMacro;
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDM)) {
            if (currentMacroNameStack.isEmpty()) {
                if (m.name.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                    List<String> lines = m.instantiate(null, code, config);
                    if (lines == null) return false;
                    macroExpansions.add(lines);
                    currentMacro = null;
                } else if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                    m.addLine(line);
                } else {
                    if (!addMacro(m, code)) {
                        return false;
                    }
                    currentMacro = null;
                }
            } else {
                currentMacroNameStack.remove(0);
                m.addLine(line);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDIF)) {
            if (currentMacroNameStack.isEmpty()) {
                if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                    List<String> lines = m.instantiate(null, code, config);
                    if (lines == null) return false;
                    macroExpansions.add(lines);
                    currentMacro = null;
                } else {
                    m.addLine(line);
                }
            } else {
                currentMacroNameStack.remove(0);
                m.addLine(line);
            }            
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ELSE)) {
            if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                m.insideElse = true;
            } else {
                m.addLine(line);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_IF)) {
            currentMacroNameStack.add(0, SourceMacro.MACRO_IF);
            m.addLine(line);
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
            currentMacroNameStack.add(0, SourceMacro.MACRO_REPT);
            m.addLine(line);
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
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_IF, s);
                m.ifCondition = s.macroCallArguments.get(0);
                if (currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
                currentMacro = m;
                return true;
            } else if (s.macroCallName.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_REPT, s);
                m.reptNRepetitions = s.macroCallArguments.get(0);
                if (currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
                currentMacro = m;                
                return true;
            } else {
                SourceMacro m = getMacro(s.macroCallName);    
                if (m != null) {
                    List<String> expandedMacro = m.instantiate(s.macroCallArguments, code, config);
                    if (expandedMacro == null) {
                        config.error("Problem instantiating macro "+s.macroCallName+" in " + source.fileName + ", " + 
                                     lineNumber + ": " + line);
                        return false;            
                    }
                    macroExpansions.add(expandedMacro);
                    return true;
                } else {
                    // macro is not yet defined, keep it in the code, and we will evaluate later
                    return false;
                }
            }
        } else {
            if (s.type == SourceStatement.STATEMENT_MACRO) {
                if (currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + source.fileName + ", " + 
                                 lineNumber + ": " + line);
                    return false;
                }
                currentMacro = new SourceMacro(s.label.name, s.macroDefinitionArgs, s.macroDefinitionDefaults, s);
                return true;
            }
            return false;
        }
    }

    
    public SourceMacro getMacro(String name)
    {
        if (macros.containsKey(name)) return macros.get(name);
        return null;
    }
    
    
    public boolean addMacro(SourceMacro m, CodeBase code) throws Exception
    {
        macros.put(m.name, m);
        if (config.idiomParser != null) {
            return config.idiomParser.newMacro(m, code);
        }
        return true;
    }
    
    
}

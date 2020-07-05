/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.SourceStatement;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author santi
 */
public class PreProcessor {

    MDLConfig config;

    // current Macro we are parsing (should be null at the end of parsing a file):
    List<String> currentMacroNameStack = new ArrayList<>();
    SourceMacro currentMacro = null;
    int macroExpansionCounter = 0;

    // Each Pair has a line and a line number:
    List<MacroExpansion> macroExpansions = new ArrayList<>();

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


    public SourceMacro getCurrentMacro()
    {
        return currentMacro;

    }


    public boolean isMacro(String name, CodeBase code)
    {
        if (getMacro(name) != null) return true;
        if (name.equalsIgnoreCase(SourceMacro.MACRO_REPT)) return true;
        if (name.equalsIgnoreCase(SourceMacro.MACRO_IF)) return true;
        if (name.equalsIgnoreCase(SourceMacro.MACRO_IFDEF)) return true;
        return false;
    }


    public SourceLine expandMacros()
    {
        while(!macroExpansions.isEmpty() && macroExpansions.get(0).lines.isEmpty()) {
            macroExpansions.remove(0);
        }
        if (!macroExpansions.isEmpty()) {
            return macroExpansions.get(0).lines.remove(0);
        } else {
            return null;
        }
    }


    // Since the statements to be added might have to be added somewhere in the middle of a file, when expanding macros at the very end of parsing,
    // we cannot insert them directly here. So, we just return a list of statements to be inserted wherever necessary by the calling code:
    // - the return value is null if failure, and a list of statements if succeeded
    public List<SourceStatement> parseMacroLine(List<String> tokens, String line, int lineNumber, SourceFile f, CodeBase code, MDLConfig config)
    {
        List<SourceStatement> newStatements = new ArrayList<>();
        SourceMacro m = currentMacro;
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDM)) {
            if (currentMacroNameStack.isEmpty()) {
                if (m.name.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, f, lineNumber, null);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentMacro = null;
                } else if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                    m.addLine(line, f, lineNumber);
                } else if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IFDEF)) {
                    m.addLine(line, f, lineNumber);
                } else {
                    if (!addMacro(m, code)) {
                        return null;
                    }
                    currentMacro = null;
                }
            } else {
                currentMacroNameStack.remove(0);
                m.addLine(line, f, lineNumber);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDR)) {
            if (currentMacroNameStack.isEmpty()) {
                if (m.name.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, f, lineNumber, null);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentMacro = null;
                } else {
                    m.addLine(line, f, lineNumber);
                }
            } else {
                currentMacroNameStack.remove(0);
                m.addLine(line, f, lineNumber);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ENDIF)) {
            if (currentMacroNameStack.isEmpty()) {
                if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF) ||
                    m.name.equalsIgnoreCase(SourceMacro.MACRO_IFDEF)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, f, lineNumber, null);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentMacro = null;
                } else {
                    m.addLine(line, f, lineNumber);
                }
            } else {
                currentMacroNameStack.remove(0);
                m.addLine(line, f, lineNumber);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_ELSE)) {
            if (m.name.equalsIgnoreCase(SourceMacro.MACRO_IF) ||
                m.name.equalsIgnoreCase(SourceMacro.MACRO_IFDEF)) {
                m.insideElse = true;
            } else {
                m.addLine(line, f, lineNumber);
            }
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_IF)) {
            currentMacroNameStack.add(0, SourceMacro.MACRO_IF);
            m.addLine(line, f, lineNumber);
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_IFDEF)) {
            currentMacroNameStack.add(0, SourceMacro.MACRO_IFDEF);
            m.addLine(line, f, lineNumber);
        } else if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
            currentMacroNameStack.add(0, SourceMacro.MACRO_REPT);
            m.addLine(line, f, lineNumber);
        } else {
            m.addLine(line, f, lineNumber);
        }

        return newStatements;
    }


    public boolean handleStatement(String line, int lineNumber,
            SourceStatement s, SourceFile source, CodeBase code, boolean complainIfUndefined)
    {
        if (s.type == SourceStatement.STATEMENT_MACROCALL) {
            if (s.macroCallMacro != null) {
                MacroExpansion expandedMacro = s.macroCallMacro.instantiate(s.macroCallArguments, s, code, config);
                if (expandedMacro == null) {
                    config.error("Problem instantiating macro "+s.macroCallMacro.name+" in " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
                macroExpansions.add(0, expandedMacro);
                return true;
            } else if (s.macroCallName.equalsIgnoreCase(SourceMacro.MACRO_IF)) {
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_IF, s);
                m.preDefinedMacroArgs = s.macroCallArguments;
                if (currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
                currentMacro = m;
                return true;
            } else if (s.macroCallName.equalsIgnoreCase(SourceMacro.MACRO_IFDEF)) {
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_IFDEF, s);
                m.preDefinedMacroArgs = s.macroCallArguments;
                if (currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + source.fileName + ", " +
                                 lineNumber + ": " + line);
                    return false;
                }
                currentMacro = m;
                return true;
            } else if (s.macroCallName.equalsIgnoreCase(SourceMacro.MACRO_REPT)) {
                SourceMacro m = new SourceMacro(SourceMacro.MACRO_REPT, s);
                m.preDefinedMacroArgs = s.macroCallArguments;
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
                    MacroExpansion expandedMacro = m.instantiate(s.macroCallArguments, s, code, config);
                    if (expandedMacro == null) {
                        config.error("Problem instantiating macro "+s.macroCallName+" in " + source.fileName + ", " +
                                     lineNumber + ": " + line);
                        return false;
                    }
                    macroExpansions.add(0,expandedMacro);
                    return true;
                } else {
                    // macro is not yet defined, keep it in the code, and we will evaluate later
                    if (complainIfUndefined) {
                        config.error("Could not expand macro in " + source.fileName + ", " + lineNumber + ": " + line);
                    }
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


    public boolean addMacro(SourceMacro m, CodeBase code)
    {
        macros.put(m.name, m);
        if (config.dialectParser != null) {
            return config.dialectParser.newMacro(m, code);
        }
        return true;
    }


    public String nextMacroExpansionContextName()
    {
        macroExpansionCounter++;
        return "___expanded_macro___" + macroExpansionCounter;
    }

}

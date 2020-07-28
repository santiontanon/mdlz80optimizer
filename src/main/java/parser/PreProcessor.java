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
import java.util.HashMap;


/**
 *
 * @author santi
 */
public class PreProcessor {
    public final String MACRO_MACRO = "macro";
    public final String MACRO_ENDM = "endm";
    public final String MACRO_REPT = "rept";
    public final String MACRO_ENDR = "endr";
    public final String MACRO_IF = "if";
    public final String MACRO_IFDEF = "ifdef";
    public final String MACRO_ELSE = "else";
    public final String MACRO_ENDIF = "endif";
    
    public static class PreProcessorFileState {
        // current Macro we are parsing (should be null at the end of parsing a file):
        SourceMacro currentMacro = null;
        List<String> currentMacroNameStack = new ArrayList<>();
        List<MacroExpansion> macroExpansions = new ArrayList<>();        
    }

    public HashMap<String, String> macroSynonyms = new HashMap<>();
    // start/end keyword, e.g.: "repeat","endrepeat":
    public HashMap<String, String> dialectMacros = new HashMap<>();
    
    MDLConfig config;

    // preProcessor internal state, push at the beginning of parsing a new file, and pop at the end:
    List<PreProcessorFileState> stateStack = new ArrayList<>();
    PreProcessorFileState currentState = new PreProcessorFileState();

    int macroExpansionCounter = 0;
    
    // We might have more than one macro with the same name (with different # of parameters).
    // However, we cannot do the usual name/#params key as some might have a variable number.
    // So, we just associate macro names with a list, and then see which one are we calling:
    public LinkedHashMap<String, List<SourceMacro>> macros = new LinkedHashMap<>();


    public PreProcessor(MDLConfig a_config)
    {
        config = a_config;
    }


    // create a new preProcessor with a clean state, but linked to the previous one (sharing "macros")
    public PreProcessor(PreProcessor pp)
    {
        config = pp.config;
        macros = pp.macros;
    }
    
    
    public void pushState()
    {
        stateStack.add(0, currentState);
        currentState = new PreProcessorFileState();
    }
    
    
    public void popState()
    {
        currentState = stateStack.remove(0);
    }


    public boolean withinMacroDefinition()
    {
        return currentState.currentMacro != null;
    }


    public SourceMacro getCurrentMacro()
    {
        return currentState.currentMacro;

    }

    
    // Checks if "token" corresponds to the macro "macro":
    public boolean isMacroName(String token, String macro)
    {
        if (token.equalsIgnoreCase(macro)) {
            return true;
        }
        token = token.toLowerCase();
        if (macroSynonyms.containsKey(token)
                && macroSynonyms.get(token).equalsIgnoreCase(macro)) {
            return true;
        }
        return false;
    }

    
    public boolean isMacro(String name)
    {
        if (getMacro(name, null) != null) return true;
        if (dialectMacros.containsKey(name)) return true;
        return  (isMacroName(name, MACRO_REPT) ||
                 isMacroName(name, MACRO_IF) ||
                 isMacroName(name, MACRO_IFDEF));
    }

    
    public boolean isMacroIncludingEnds(String name)
    {
        if (getMacro(name, null) != null) return true;
        if (dialectMacros.containsKey(name)) return true;
        if (dialectMacros.containsValue(name)) return true;
        return  (isMacroName(name, MACRO_REPT) ||
                 isMacroName(name, MACRO_IF) ||
                 isMacroName(name, MACRO_IFDEF) ||
                 isMacroName(name, MACRO_ENDM) ||
                 isMacroName(name, MACRO_ENDR) ||
                 isMacroName(name, MACRO_ELSE) ||
                 isMacroName(name, MACRO_ENDIF));
    }
    

    public SourceLine expandMacros()
    {
        while(!currentState.macroExpansions.isEmpty() && currentState.macroExpansions.get(0).lines.isEmpty()) {
            currentState.macroExpansions.remove(0);
        }
        if (!currentState.macroExpansions.isEmpty()) {
            return currentState.macroExpansions.get(0).lines.remove(0);
        } else {
            return null;
        }
    }


    // Since the statements to be added might have to be added somewhere in the middle of a file, when expanding macros at the very end of parsing,
    // we cannot insert them directly here. So, we just return a list of statements to be inserted wherever necessary by the calling code:
    // - the return value is null if failure, and a list of statements if succeeded
    public List<SourceStatement> parseMacroLine(List<String> tokens, SourceLine sl, SourceFile f, CodeBase code, MDLConfig config)
    {
        List<SourceStatement> newStatements = new ArrayList<>();
        SourceMacro m = currentState.currentMacro;
        if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_ENDM)) {
            if (currentState.currentMacroNameStack.isEmpty()) {
                if (isMacroName(m.name, MACRO_REPT)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, sl, f);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentState.currentMacro = null;

                } else if (isMacroName(m.name, MACRO_IF) ||
                           isMacroName(m.name, MACRO_IFDEF)) {
                    m.addLine(sl);

                } else if (dialectMacros.containsKey(m.name)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, sl, f);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentState.currentMacro = null;                    
                } else {
                    if (!addMacro(m, code)) {
                        return null;
                    }
                    currentState.currentMacro = null;
                }
            } else {
                currentState.currentMacroNameStack.remove(0);
                m.addLine(sl);
            }
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_ENDR)) {
            if (currentState.currentMacroNameStack.isEmpty()) {
                if (isMacroName(m.name, MACRO_REPT)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, sl, f);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentState.currentMacro = null;
                } else {
                    m.addLine(sl);
                }
            } else {
                currentState.currentMacroNameStack.remove(0);
                m.addLine(sl);
            }
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_ENDIF)) {
            if (currentState.currentMacroNameStack.isEmpty()) {
                if (isMacroName(m.name, MACRO_IF) ||
                    isMacroName(m.name, MACRO_IFDEF)) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, sl, f);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    newStatements.add(s);
                    currentState.currentMacro = null;
                } else {
                    m.addLine(sl);
                }
            } else {
                currentState.currentMacroNameStack.remove(0);
                m.addLine(sl);
            }
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_ELSE)) {
            if (currentState.currentMacroNameStack.isEmpty() &&
                (isMacroName(m.name, MACRO_IF) ||
                 isMacroName(m.name, MACRO_IFDEF))) {
                m.insideElse = true;
            } else {
                m.addLine(sl);
            }
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_IF)) {
            currentState.currentMacroNameStack.add(0, MACRO_IF);
            m.addLine(sl);
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_IFDEF)) {
            currentState.currentMacroNameStack.add(0, MACRO_IFDEF);
            m.addLine(sl);
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_REPT)) {
            currentState.currentMacroNameStack.add(0, MACRO_REPT);
            m.addLine(sl);
        } else if (!tokens.isEmpty() && dialectMacros.containsKey(tokens.get(0).toLowerCase())) {
            currentState.currentMacroNameStack.add(0, MACRO_REPT);
            m.addLine(sl);
        } else if (!tokens.isEmpty() && dialectMacros.containsValue(tokens.get(0).toLowerCase())) {
            if (currentState.currentMacroNameStack.isEmpty()) {
                SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_MACROCALL, sl, f);
                s.macroCallMacro = m;
                s.macroCallArguments = m.preDefinedMacroArgs;
                newStatements.add(s);
                currentState.currentMacro = null;
            } else {
                currentState.currentMacroNameStack.remove(0);
                m.addLine(sl);
            }
        } else {
            m.addLine(sl);
        }

        return newStatements;
    }


    public List<SourceStatement> handleStatement(SourceLine sl,
            SourceStatement s, SourceFile source, CodeBase code, boolean complainIfUndefined)
    {
        List<SourceStatement> l = new ArrayList<>();
        
        if (s.type == SourceStatement.STATEMENT_MACROCALL) {
            if (s.macroCallMacro != null) {
                MacroExpansion expandedMacro = s.macroCallMacro.instantiate(s.macroCallArguments, s, code, config);
                if (expandedMacro == null) {
                    config.error("Problem instantiating macro "+s.macroCallMacro.name+" in " + sl);
                    return null;
                }
                
                if (s.label != null) {
                    SourceStatement auxiliar = new SourceStatement(SourceStatement.STATEMENT_NONE, s.sl, s.source);
                    auxiliar.label = s.label;
                    auxiliar.labelPrefix = s.labelPrefix;
                    l.add(auxiliar);
                }
                
                currentState.macroExpansions.add(0, expandedMacro);
                return l;
            } else if (isMacroName(s.macroCallName, MACRO_IF)) {
                SourceMacro m = new SourceMacro(MACRO_IF, s);
                m.preDefinedMacroArgs = s.macroCallArguments;
                if (currentState.currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + sl);
                    return null;
                }
                currentState.currentMacro = m;
                return l;

            } else if (isMacroName(s.macroCallName, MACRO_IFDEF)) {
                SourceMacro m = new SourceMacro(MACRO_IFDEF, s);
                m.preDefinedMacroArgs = s.macroCallArguments;
                if (currentState.currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + sl);
                    return null;
                }
                currentState.currentMacro = m;
                return l;

            } else if (isMacroName(s.macroCallName, MACRO_REPT)) {
                SourceMacro m = new SourceMacro(MACRO_REPT, s);
                m.preDefinedMacroArgs = s.macroCallArguments;
                if (currentState.currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + sl);
                    return null;
                }
                currentState.currentMacro = m;
                return l;
            } else if (dialectMacros.containsKey(s.macroCallName.toLowerCase())) {
                SourceMacro m = new SourceMacro(s.macroCallName.toLowerCase(), s);
                m.preDefinedMacroArgs = s.macroCallArguments;
                if (currentState.currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + sl);
                    return null;
                }
                currentState.currentMacro = m;
                return l;

            } else {
                
                SourceMacro m = getMacro(s.macroCallName, s.macroCallArguments.size());
                if (m != null) {
                    MacroExpansion expandedMacro = m.instantiate(s.macroCallArguments, s, code, config);
                    if (expandedMacro == null) {
                        config.error("Problem instantiating macro "+s.macroCallName+" in " + sl);
                        return null;
                    }
                    if (s.label != null) {
                        SourceStatement auxiliar = new SourceStatement(SourceStatement.STATEMENT_NONE, s.sl, s.source);
                        auxiliar.label = s.label;
                        auxiliar.labelPrefix = s.labelPrefix;
                        l.add(auxiliar);
                    }                    
                    currentState.macroExpansions.add(0, expandedMacro);
                    return l;
                } else {
                    // macro is not yet defined, keep it in the code, and we will evaluate later
                    if (complainIfUndefined) {
                        config.error("Could not expand macro in " + sl);
                    }
                    return null;
                }
            }
        } else {
            if (s.type == SourceStatement.STATEMENT_MACRO) {
                if (currentState.currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + sl);
                    return null;
                }
                currentState.currentMacro = new SourceMacro(s.label.name, s.macroDefinitionArgs, s.macroDefinitionDefaults, s);
                return l;
            }
            return null;
        }
    }


    public SourceMacro getMacro(String name, Integer nParams)
    {
        if (macros.containsKey(name)) {
            List<SourceMacro> l = macros.get(name);
            
            // If we don't know the number of parameters, just return the first:
            if (nParams == null) return l.get(0);
            
            // If we know, and there's several alternatives, see if any matches:
            for(SourceMacro m:l) {
                if (m.variableNumberofArgs || m.argNames.size() == nParams) return m;
            }
            
            // Otherwise, maybe there are default arguments, so, just return the first:
            return l.get(0);
        }
        return null;
    }


    public boolean addMacro(SourceMacro m, CodeBase code)
    {
        List<SourceMacro> l = macros.get(m.name);
        if (l == null) {
            l = new ArrayList<>();
            macros.put(m.name, l);
        }
        l.add(m);
        return true;
    }


    public String nextMacroExpansionContextName()
    {
        macroExpansionCounter++;
        return "___expanded_macro___" + macroExpansionCounter;
    }

}

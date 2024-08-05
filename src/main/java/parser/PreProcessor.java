/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.util.HashMap;
import util.TextUtils;


/**
 *
 * @author santi
 */
public class PreProcessor {
    public String MACRO_MACRO = "macro";
    public String MACRO_ENDM = "endm";
    public String MACRO_REPT = "rept";
    public String MACRO_ENDR = "endr";
    public String MACRO_IF = "if";
    public String MACRO_IFDEF = "ifdef";
    public String MACRO_IFNDEF = "ifndef";
    public String MACRO_ELSE = "else";
    public String MACRO_ENDIF = "endif";
    
    public boolean addScopeLabelsToRept = false;
    
    // Some dialects define many variants of conditionals, such as "ifexists", "ifdifni", etc.
    // which might have to be evaluated eagerly, those are added here:
    public List<String> dialectIfs = new ArrayList<>();
    
    public String temporaryLabelArgPrefix = null;
    
    public String unnamedMacroPrefix = "___expanded_macro___";
    
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
    
    public LinkedHashMap<String, List<TextMacro>> textMacros = new LinkedHashMap<>();


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
    
    
    public int getCurrentMacroStateNExpansions()
    {
        return currentState.macroExpansions.size();
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
        if (dialectMacros.containsKey(name.toLowerCase())) return true;
        return  (isMacroName(name, MACRO_REPT) ||
                 isMacroName(name, MACRO_IF) ||
                 isMacroName(name, MACRO_IFDEF) ||
                 isMacroName(name, MACRO_IFNDEF));
    }

    
    public boolean isMacroIncludingEnds(String name)
    {
        if (getMacro(name, null) != null) return true;
        if (dialectMacros.containsKey(name.toLowerCase())) return true;
        if (dialectMacros.containsValue(name.toLowerCase())) return true;
        return  (isMacroName(name, MACRO_REPT) ||
                 isMacroName(name, MACRO_IF) ||
                 isMacroName(name, MACRO_IFDEF) ||
                 isMacroName(name, MACRO_IFNDEF) ||
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

    
    SourceConstant macroCallLabel(CodeStatement macroCall, CodeBase code)
    {
        if (macroCall.label != null) {
            return macroCall.label;
//        } else {
//            CodeStatement previous = macroCall.source.getPreviousStatementTo(macroCall, code);
//            if (previous.source == macroCall.source && previous.label != null && previous.type == CodeStatement.STATEMENT_NONE) {
//                return previous.label;
//            }
        }
        return null;
    }
    

    // Since the statements to be added might have to be added somewhere in the middle of a file, when expanding macros at the very end of parsing,
    // we cannot insert them directly here. So, we just return a list of statements to be inserted wherever necessary by the calling code:
    // - the return value is null if failure, and a list of statements if succeeded
    public List<CodeStatement> parseMacroLine(List<String> tokens, SourceLine sl, SourceFile f, CodeBase code, MDLConfig config)
    {
        List<CodeStatement> newStatements = new ArrayList<>();
        SourceMacro m = currentState.currentMacro;
        if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_ENDM)) {
            if (currentState.currentMacroNameStack.isEmpty()) {
                if (isMacroName(m.name, MACRO_REPT)) {
                    CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_MACROCALL, sl, f, config);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    s.label = macroCallLabel(m.definingStatement, code);
                    s.labelPrefix = m.definingStatement.labelPrefix;
                    m.definingStatement = s;
                    newStatements.add(s);
                    currentState.currentMacro = null;

                } else if (isMacroName(m.name, MACRO_IF) ||
                           isMacroName(m.name, MACRO_IFDEF) ||
                           isMacroName(m.name, MACRO_IFNDEF)) {
                    m.addLine(sl);

                } else if (dialectMacros.containsKey(m.name)) {
                    CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_MACROCALL, sl, f, config);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    s.label = macroCallLabel(m.definingStatement, code);
                    s.labelPrefix = m.definingStatement.labelPrefix;
                    m.definingStatement = s;
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
                    CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_MACROCALL, sl, f, config);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    s.label = macroCallLabel(m.definingStatement, code);
                    s.labelPrefix = m.definingStatement.labelPrefix;
                    m.definingStatement = s;
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
                    isMacroName(m.name, MACRO_IFDEF) ||
                    isMacroName(m.name, MACRO_IFNDEF) ||
                    (dialectMacros.containsKey(m.name) &&
                     dialectMacros.get(m.name).equalsIgnoreCase(tokens.get(0)))) {
                    CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_MACROCALL, sl, f, config);
                    s.macroCallMacro = m;
                    s.macroCallArguments = m.preDefinedMacroArgs;
                    s.label = macroCallLabel(m.definingStatement, code);
                    s.labelPrefix = m.definingStatement.labelPrefix;
                    m.definingStatement = s;
                    currentState.currentMacro = null;
                    
                    newStatements.add(s);                    
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
                 isMacroName(m.name, MACRO_IFDEF) ||
                 isMacroName(m.name, MACRO_IFNDEF) ||
                 TextUtils.anyMatchesIgnoreCase(m.name, dialectIfs))) {
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
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_IFNDEF)) {
            currentState.currentMacroNameStack.add(0, MACRO_IFNDEF);
            m.addLine(sl);
        } else if (!tokens.isEmpty() && isMacroName(tokens.get(0), MACRO_REPT)) {
            currentState.currentMacroNameStack.add(0, MACRO_REPT);
            m.addLine(sl);
        } else if (!tokens.isEmpty() && dialectMacros.containsKey(tokens.get(0).toLowerCase())) {
            currentState.currentMacroNameStack.add(0, tokens.get(0).toLowerCase());
            m.addLine(sl);
        } else if (!tokens.isEmpty() && dialectMacros.containsValue(tokens.get(0).toLowerCase())) {
            if (currentState.currentMacroNameStack.isEmpty()) {
                CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_MACROCALL, sl, f, config);
                s.macroCallMacro = m;
                s.macroCallArguments = m.preDefinedMacroArgs;
                s.label = macroCallLabel(m.definingStatement, code);
                s.labelPrefix = m.definingStatement.labelPrefix;
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


    public List<CodeStatement> handleStatement(SourceLine sl,
            CodeStatement s, SourceFile source, CodeBase code, boolean expandMacroCalls, boolean errorMessageIfUnableToExpand)
    {
        List<CodeStatement> l = new ArrayList<>();
                
        if (s.type == CodeStatement.STATEMENT_MACROCALL) {
            if (s.macroCallMacro != null) {                
                if (!expandMacroCalls) return null;
                
                MacroExpansion expandedMacro = s.macroCallMacro.instantiate(s.macroCallArguments, s, code, config);
                if (expandedMacro == null) {
                    config.error("Problem instantiating macro "+s.macroCallMacro.name+" in " + sl);
                    return null;
                }
                
                if (s.label != null) {
                    CodeStatement auxiliar = new CodeStatement(CodeStatement.STATEMENT_NONE, s.sl, s.source, config);
                    auxiliar.label = s.label;
                    auxiliar.labelPrefix = s.labelPrefix;
                    auxiliar.label.definingStatement = auxiliar;
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
                
            } else if (isMacroName(s.macroCallName, MACRO_IFNDEF)) {
                SourceMacro m = new SourceMacro(MACRO_IFNDEF, s);
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
                if (!expandMacroCalls) return null;
                
                SourceMacro m = getMacro(s.macroCallName, s.macroCallArguments.size());
                if (m != null) {
                    MacroExpansion expandedMacro = m.instantiate(s.macroCallArguments, s, code, config);
                    if (expandedMacro == null) {
                        config.error("Problem instantiating macro "+s.macroCallName+" in " + sl);
                        return null;
                    }
                    if (s.label != null) {
                        CodeStatement auxiliar = new CodeStatement(CodeStatement.STATEMENT_NONE, s.sl, s.source, config);
                        auxiliar.label = s.label;
                        auxiliar.labelPrefix = s.labelPrefix;
                        auxiliar.label.definingStatement = auxiliar;
                        l.add(auxiliar);
                    }           
                    currentState.macroExpansions.add(0, expandedMacro);
                    return l;
                } else {
                    // macro is not yet defined, keep it in the code, and we will evaluate later
                    if (expandMacroCalls && errorMessageIfUnableToExpand) {
                        config.error("Could not expand macro in " + sl);
                    }
                    return null;
                }
            }
        } else {
            if (s.type == CodeStatement.STATEMENT_MACRO) {
                if (currentState.currentMacro != null) {
                    config.error("Something weird just happend (expanding two macros at once, contact the developer) in " + sl);
                    return null;
                }
                currentState.currentMacro = new SourceMacro(s.label.name, s.macroDefinitionArgs, s.macroDefinitionDefaults, s, config);
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
        config.debug("adding macro: " + m.name);
        List<SourceMacro> l = macros.get(m.name);
        if (l == null) {
            l = new ArrayList<>();
            macros.put(m.name, l);
        }
        l.add(m);
        return true;
    }


    public boolean addTextMacro(TextMacro m)
    {
        config.debug("adding text macro: " + m.name + "/" + m.argNames.size() + "  as  " + m.tokens);
        List<TextMacro> l = textMacros.get(m.name);
        if (l == null) {
            l = new ArrayList<>();
            textMacros.put(m.name, l);
        }
        
        // check if it's a redefinition:
        TextMacro found = null;
        for(TextMacro m2:l) {
            if (m.argNames.size() == m2.argNames.size()) {
                found = m2;
                break;
            }
        }
        if (found != null) l.remove(found);
        l.add(m);
        return true;
    }
    
    
    public TextMacro getTextMacro(String name, int nargs)
    {
        List<TextMacro> l = textMacros.get(name);
        if (l == null) return null;
        for(TextMacro m:l) {
            if (m.argNames.size() == nargs) return m;
        }
        return null;
    }
    
    
    public boolean removeTextMacro(String name, int nargs)
    {
        List<TextMacro> l = textMacros.get(name);
        if (l == null) return false;
        TextMacro found = null;
        for(TextMacro m:l) {
            if (m.argNames.size() == nargs) {
                found = m;
                break;
            }
        }
        if (found == null) return false;
        l.remove(found);
        if (l.isEmpty()) {
            textMacros.remove(name);
        }
        return true;
        
    }


    public String nextMacroExpansionContextName(String labelPrefix, CodeBase code)
    {
        String name;
        while(true) {
            macroExpansionCounter++;
            if (labelPrefix == null) {
                name = unnamedMacroPrefix + macroExpansionCounter;
            } else {
                name = labelPrefix + unnamedMacroPrefix + macroExpansionCounter;
            }
            if (code.getSymbol(name) == null &&
                code.getSymbolWithPrefix(name + ".") == null) {
                return name;
            }
        }
    }
    
    
    public void expandTextMacros(List<String> tokens, CodeStatement s, SourceLine sl)
    {        
        for(int i = 0;i<tokens.size();i++) {
            String token = tokens.get(i);
            List<TextMacro> matches = textMacros.get(token);
            if (matches != null) {                
                // detect number of arguments:
                List<List<String>> argumentTokens = new ArrayList<>();
                int startToken = i;
                int endToken = i + 1;
                TextMacro match = null;
                if (i < tokens.size()-1 && tokens.get(i+1).equals("(")) {
                    List<String> currentArgumentTokens = new ArrayList<>();
                    int parenthesis = 1;
                    for(int j = i+2;j<tokens.size();j++) {
                        if (tokens.get(j).equals(",")) {
                            if (parenthesis == 1) {
                                argumentTokens.add(currentArgumentTokens);
                                currentArgumentTokens = new ArrayList<>();
                            } else {
                                currentArgumentTokens.add(tokens.get(j));
                            }
                        } else if (tokens.get(j).equals("(")) {
                            parenthesis ++;
                            currentArgumentTokens.add(tokens.get(j));
                        } else if (tokens.get(j).equals(")")) {
                            parenthesis --;
                            if (parenthesis == 0) {
                                if (!currentArgumentTokens.isEmpty()) {
                                    argumentTokens.add(currentArgumentTokens);
                                }
                                endToken = j + 1;
                                break;
                            }
                            currentArgumentTokens.add(tokens.get(j));
                        } else {
                            currentArgumentTokens.add(tokens.get(j));
                        }
                    }
                    if (parenthesis == 0) {
                        // we have a potential parameter list, try to parse it!                        
                        for(TextMacro m:matches) {
                            if (m.argNames.size() == argumentTokens.size()) {
                                match = m;
                                break;
                            }
                        }
                    }
                }
                if (match == null) {
                    for(TextMacro m:matches) {
                        if (m.argNames.isEmpty()) {
                            match = m;
                            endToken = i + 1;
                            break;
                        }
                    }
                }

                if (match != null) {
                    int nTokensToRemove = endToken - startToken;

                    // remove the macro tokens:
                    for(int k = 0;k<nTokensToRemove;k++) {
                        tokens.remove(startToken);
                    }

                    // instantiate the macro:
                    List<String> instantiatedTokens = match.instantiate(argumentTokens);

                    // check for line splits:
                    List<List<String>> lines = new ArrayList<>();
                    List<String> line = new ArrayList<>();
                    for(String itoken: instantiatedTokens) {
                        if (itoken.equals("\\")) {
                            // new line!
                            lines.add(line);
                            line = new ArrayList<>();
                        } else {
                            line.add(itoken);
                        }
                    }
                    if (!line.isEmpty()) lines.add(line);

                    if (!lines.isEmpty()) {
                        line = lines.remove(0);

                        if (lines.isEmpty()) {
                            tokens.addAll(startToken, line);
                        } else {
                            // push all the left-over tokens after the macro to the last line:
                            while(tokens.size() > startToken) {
                                lines.get(lines.size()-1).add(tokens.remove(startToken));
                            }
                            tokens.addAll(startToken, line);

                            // add the rest of the lines to the next lines to parse:
                            List<SourceLine> s_lines = new ArrayList<>();
                            for(List<String> line2:lines) {
                                String concatenated = "";
                                for(String token2:line2) {
                                    concatenated += token2 += " ";
                                }
                                s_lines.add(new SourceLine(concatenated, sl.source, sl.lineNumber));
                            }
                            MacroExpansion expansion = new MacroExpansion(match, s, s_lines);
                            currentState.macroExpansions.add(0, expansion);
                        }
                    }

                    // recurse to see if there are more macros to expand:
                    expandTextMacros(tokens, s, sl);
                    return;
                }
            }
        }        
    }
}

/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.ArrayList;
import java.util.List;

import code.CodeStatement;


public class TextMacro {
    public String name = null;
    public List<String> argNames = new ArrayList<>();
    public List<String> tokens = new ArrayList<>();
    public CodeStatement definingStatement = null;
    
    public TextMacro(String a_name, List<String> a_args, List<String> a_tokens, CodeStatement a_definingStatement) {
        name = a_name;
        argNames = a_args;
        tokens = a_tokens;
        definingStatement = a_definingStatement;
    }
    
    
    public List<String> instantiate(List<List<String>> argValues)
    {
        List<String> instantiation = new ArrayList<>();
        
        for(String token:tokens) {
            int idx = argNames.indexOf(token);
            if (idx == -1) {
                instantiation.add(token);
            } else {
                instantiation.addAll(argValues.get(idx));
            }
        }
        
        return instantiation;
    }
}


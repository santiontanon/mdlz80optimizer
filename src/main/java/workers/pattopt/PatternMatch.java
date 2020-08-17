/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author santi
 */
public class PatternMatch {
    public Pattern pattern;
    public HashMap<Integer, List<SourceStatement>> map = new HashMap<>();
    public HashMap<String, Expression> variables = new HashMap<>();
    
    // If the pattern is applied, these will be added to the list of equalities to check later on:
    public List<EqualityConstraint> newEqualities = new ArrayList<>();
    
    // If the pattern is applied, we record the set of SourceStatements that were added, and those that were removed:
    SourceFile f = null;
    List<SourceStatement> removed = new ArrayList<>();
    List<SourceStatement> added = new ArrayList<>();
    
    public PatternMatch(Pattern a_patt, SourceFile a_f)
    {
        pattern = a_patt;
        f = a_f;
    }
    
    
    public PatternMatch(PatternMatch m)
    {
        map.putAll(m.map);
        variables.putAll(m.variables);
    }
    
    
    public boolean addVariableMatch(String variable, Expression value)
    {
        if (variables.containsKey(variable)) {
            Expression oldValue = variables.get(variable);
            // todo(santi@): proper expression comparison
            if (oldValue.toString().equals(value.toString())) return true;
            return false;
        } else {
            variables.put(variable, value);
            return true;
        }
    }
}

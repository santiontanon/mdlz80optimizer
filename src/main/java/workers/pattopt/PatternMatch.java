/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import code.Expression;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author santi
 */
public class PatternMatch {
    public HashMap<Integer, SourceStatement> opMap = new HashMap<>();
    public HashMap<Integer, List<SourceStatement>> wildCardMap = new HashMap<>();
    public HashMap<String, Expression> variables = new HashMap<>();
    
    // If the pattern is applied, these will be added to the list of equalities to check later on:
    public List<EqualityConstraint> newEqualities = new ArrayList<>();
    
    public PatternMatch()
    {
    }
    
    
    public PatternMatch(PatternMatch m)
    {
        opMap.putAll(m.opMap);
        wildCardMap.putAll(m.wildCardMap);
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

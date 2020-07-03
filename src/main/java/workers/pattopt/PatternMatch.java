/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import code.Expression;
import code.SourceStatement;
import java.util.HashMap;

/**
 *
 * @author santi
 */
public class PatternMatch {
    public HashMap<Integer, SourceStatement> opMap = new HashMap<>();
    public HashMap<String, Expression> variables = new HashMap<>();
    
    
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

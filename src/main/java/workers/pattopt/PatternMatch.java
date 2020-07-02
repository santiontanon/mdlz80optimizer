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
    public HashMap<String, Expression> variablesMatched = new HashMap<>();
}

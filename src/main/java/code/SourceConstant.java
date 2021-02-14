/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.util.List;

public class SourceConstant {
    MDLConfig config;
            
    public String name;
    public String originalName; // name before scoping
    public Expression exp;
    public String colonTokenUsedInDefinition = null;  // In some dialects (e.g. sdasz80), differrent types
                                                      // of "colons" (":" vs "::") have different semantics
    Object valueCache;  // null if not yet evaluated
    
    public boolean resolveEagerly = false; // Variables where this is true, will be evaluated right away
                                           // This is needed for := variables in sjasm and asMSX
    
    public SourceStatement definingStatement;  // the statement where it was defined
    
    public SourceConstant(String a_name, String a_originalName, Expression a_exp, SourceStatement a_s, MDLConfig config)
    {
        name = a_name;
        originalName = a_originalName;
        valueCache = null;
        exp = a_exp;
        definingStatement = a_s;
    }
    
    
    public Object getValue(CodeBase code, boolean silent)
    {
        if (valueCache != null) {
            return valueCache;
        } else {
            valueCache = exp.evaluate(definingStatement, code, silent);
            return valueCache;
        }
    }


    public Object getValueInternal(CodeBase code, boolean silent, SourceStatement previous, List<String> variableStack)
    {
        if (valueCache != null) {
            return valueCache;
        } else {
            if (variableStack.contains(name)) {
                config.warn("Circular dependency on " +this+ " when evaluating expression");
                return null;
            }
            variableStack.add(name);
            valueCache = exp.evaluateInternal(definingStatement, code, silent, previous, variableStack);
            return valueCache;
        }
    }
    
    
    public void clearCache()
    {
        valueCache = null;
    }
    
    
    public boolean isLabel()
    {
        return exp != null &&
               exp.type == Expression.EXPRESSION_SYMBOL && 
               exp.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS);
    }
}

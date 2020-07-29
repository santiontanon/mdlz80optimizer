/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

public class SourceConstant {
    public String name;
    public Expression exp;
    Number valueCache;  // null if not yet evaluated
    
    public boolean resolveEagerly = false; // Variables where this is true, will be evaluated right away
                                           // This is needed for := variables in sjasm and asMSX
    
    public SourceStatement definingStatement;  // the statement where it was defined
    
    public SourceConstant(String a_name, Number a_value, Expression a_exp, SourceStatement a_s)
    {
        name = a_name;
        valueCache = a_value;
        exp = a_exp;
        definingStatement = a_s;
    }
    
    
    public Number getValue(CodeBase code, boolean silent)
    {
        if (valueCache != null) {
            return valueCache;
        } else {
            valueCache = exp.evaluate(definingStatement, code, silent);
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

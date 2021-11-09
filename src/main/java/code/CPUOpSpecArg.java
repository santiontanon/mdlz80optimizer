/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package code;

/**
 *
 * @author santi
 */
public class CPUOpSpecArg {
    public String reg = null;
    public String regIndirection = null;
    public String regOffsetIndirection = null;
    public String condition = null;
    public boolean byteConstantIndirectionAllowed = false;
    public boolean wordConstantIndirectionAllowed = false;
    public boolean byteConstantAllowed = false;
    public boolean wordConstantAllowed = false;
    public boolean wordConstantBigEndianAllowed = false;
    public boolean relativeLabelAllowed = false;
    public Integer min = null;
    public Integer max = null;


    public boolean match(Expression exp, CPUOpSpec spec, CodeStatement s, CodeBase code)
    {
        if (condition != null) {
            if (exp.type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                exp.registerOrFlagName.equalsIgnoreCase(condition)) return true;
        }
        if (reg != null) {
            if (exp.type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                spec.regMatch(reg, exp.registerOrFlagName)) return true;
        }
        if (regIndirection != null) {
            if (exp.type == Expression.EXPRESSION_PARENTHESIS &&
                exp.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                spec.regMatch(regIndirection, exp.args.get(0).registerOrFlagName)) return true;
        }
        if (regOffsetIndirection != null) {
            if (exp.type == Expression.EXPRESSION_PARENTHESIS) {
                Expression exp2 = exp.args.get(0);
                if (exp2.type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                    spec.regMatch(regOffsetIndirection, exp2.registerOrFlagName)) return true;
                if (exp2.type == Expression.EXPRESSION_SUM ||
                    exp2.type == Expression.EXPRESSION_SUB) {
                    if (exp2.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                        spec.regMatch(regOffsetIndirection, exp2.args.get(0).registerOrFlagName) &&
                        exp2.args.get(1).evaluatesToIntegerConstant()) {
                        return true;
                    }
                }
            }
        }
        if (byteConstantIndirectionAllowed ||
            wordConstantIndirectionAllowed) {
            if (exp.type == Expression.EXPRESSION_PARENTHESIS &&
                exp.args.get(0).evaluatesToIntegerConstant()) {
                return true;
            }
        }
        if (byteConstantAllowed ||
            wordConstantAllowed ||
            wordConstantBigEndianAllowed ||
            relativeLabelAllowed) {
            if (exp.type != Expression.EXPRESSION_PARENTHESIS &&
                exp.evaluatesToIntegerConstant()) {
                if (exp.isConstant()) {
                    Integer v = exp.evaluateToInteger(s, code, true);
                    if (v == null) return true;
                    if (min == null) {
                        if (max == null) {
                            return true;
                        } else {
                            if (v<=max) return true;
                        }
                    } else {
                        if (max == null) {
                            if (v>=min) return true;
                        } else {
                            if (v>=min && v<=max) return true;
                        }
                    }                    
                } else {
                    // do not evaluateToInteger if it's not a constant, as this can trigger complex address dereferencing...
                    return true;
                }
            }
        }
        return false;
    }    
    
    
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof CPUOpSpecArg)) return false;
        CPUOpSpecArg arg = (CPUOpSpecArg)o;
        
        if (reg == null && arg.reg != null) return false;
        if (reg != null && !reg.equals(arg.reg)) return false;

        if (regIndirection == null && arg.regIndirection != null) return false;
        if (regIndirection != null && !regIndirection.equals(arg.regIndirection)) return false;

        if (regOffsetIndirection == null && arg.regOffsetIndirection != null) return false;
        if (regOffsetIndirection != null && !regOffsetIndirection.equals(arg.regOffsetIndirection)) return false;

        if (condition == null && arg.condition != null) return false;
        if (condition != null && !condition.equals(arg.condition)) return false;
        
        if (byteConstantIndirectionAllowed != arg.byteConstantIndirectionAllowed) return false;
        if (wordConstantIndirectionAllowed != arg.wordConstantIndirectionAllowed) return false;
        if (byteConstantAllowed != arg.byteConstantAllowed) return false;
        if (wordConstantAllowed != arg.wordConstantAllowed) return false;
        if (wordConstantBigEndianAllowed != arg.wordConstantBigEndianAllowed) return false;
        if (relativeLabelAllowed != arg.relativeLabelAllowed) return false;
        
        if (min == null && arg.min != null) return false;
        if (min != null && !min.equals(arg.min)) return false;

        if (max == null && arg.max != null) return false;
        if (max != null && !max.equals(arg.max)) return false;
        
        return true;
    }
}

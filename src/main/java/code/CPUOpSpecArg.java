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
    public boolean relativeLabelAllowed = false;
    public Integer min = null;
    public Integer max = null;


    public boolean match(Expression exp, CPUOpSpec spec, SourceStatement s, CodeBase code)
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
                        exp2.args.get(1).evaluatesToNumericConstant()) {
                        return true;
                    }
                }
            }
        }
        if (byteConstantIndirectionAllowed ||
            wordConstantIndirectionAllowed) {
            if (exp.type == Expression.EXPRESSION_PARENTHESIS &&
                exp.args.get(0).evaluatesToNumericConstant()) {
                return true;
            }
        }
        if (byteConstantAllowed ||
            wordConstantAllowed ||
            relativeLabelAllowed) {
            if (exp.type != Expression.EXPRESSION_PARENTHESIS &&
                exp.evaluatesToNumericConstant()) {
                Integer v = exp.evaluate(s, code, true);
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
            }
        }
        return false;
    }    
}

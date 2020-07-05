/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;

public class Expression {
    public static final int TRUE = -1;
    public static final int FALSE = 0;

    public static final int EXPRESSION_REGISTER_OR_FLAG = 0;
    public static final int EXPRESSION_NUMERIC_CONSTANT = 1;
    public static final int EXPRESSION_STRING_CONSTANT = 2;
    public static final int EXPRESSION_SYMBOL = 3;
    public static final int EXPRESSION_SIGN_CHANGE = 4;
    public static final int EXPRESSION_PARENTHESIS = 5;
    public static final int EXPRESSION_SUM = 6;
    public static final int EXPRESSION_SUB = 7;
    public static final int EXPRESSION_MUL = 8;
    public static final int EXPRESSION_DIV = 9;
    public static final int EXPRESSION_MOD = 10;
    public static final int EXPRESSION_OR  = 11;
    public static final int EXPRESSION_AND = 12;
    public static final int EXPRESSION_EQUAL = 13;
    public static final int EXPRESSION_LOWERTHAN = 14;
    public static final int EXPRESSION_GREATERTHAN = 15;
    public static final int EXPRESSION_LEQTHAN = 16;
    public static final int EXPRESSION_GEQTHAN = 17;
    public static final int EXPRESSION_DIFF = 18;
    public static final int EXPRESSION_TERNARY_IF = 19;
    public static final int EXPRESSION_LSHIFT = 20;
    public static final int EXPRESSION_RSHIFT = 21;
    public static final int EXPRESSION_BITOR = 22;
    public static final int EXPRESSION_BITAND = 23;
    public static final int EXPRESSION_BITNEGATION = 24;
    public static final int EXPRESSION_BITXOR = 25;
    public static final int EXPRESSION_LOGICAL_NEGATION = 26;

    // indexed by the numbers above:
    public static final int OPERATOR_PRECEDENCE[] = {
        -1, -1, -1, -1, -1,
        -1,  6,  6,  5,  5,
         5, 13, 11, 10,  9,
         9,  9,  9, 10, 16,
         7,  7, 11, 13,  3,
         12};
    
    MDLConfig config;
    public int type;
    public int numericConstant;
    public String stringConstant;
    public String symbolName;
    public String registerOrFlagName;
    public List<Expression> args= null;
    
    private Expression(int a_type, MDLConfig a_config)
    {
        type = a_type;
        config = a_config;
    }


    public Integer evaluate(SourceStatement s, CodeBase code, boolean silent)
    {
        switch(type) {
            case EXPRESSION_NUMERIC_CONSTANT:
                return numericConstant;

            case EXPRESSION_STRING_CONSTANT:
                if (stringConstant.length() == 1) {
                    return (int)stringConstant.charAt(0);
                } else {
                    config.warn("A string cannot be used as part of an expression.");
                    return null;
                }

            case EXPRESSION_SYMBOL:
                {
                    if (symbolName.equals(CodeBase.CURRENT_ADDRESS)) return s.getAddress(code);
                    Integer value = code.getSymbolValue(symbolName, silent);
                    if (value == null) {
                        if (!silent) {
                            config.error("Undefined symbol " + symbolName);
                            code.getSymbolValue(symbolName, silent);
                        }
                        return null;
                    }
                    return value;
                }

            case EXPRESSION_SIGN_CHANGE:
                {
                    Integer v = args.get(0).evaluate(s, code, silent);
                    if (v == null) return null;
                    return -v;
                }

            case EXPRESSION_PARENTHESIS:
                {
                    Integer v = args.get(0).evaluate(s, code, silent);
                    if (v == null) return null;
                    return v;
                }

            case EXPRESSION_SUM:
                {
                    int accum = 0;
                    for(Expression arg:args) {
                        Integer v = arg.evaluate(s, code, silent);
                        if (v == null) return null;
                        accum += v;
                    }
                    return accum;
                }

            case EXPRESSION_SUB:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 - v2;
                }

            case EXPRESSION_MUL:
                {
                    int accum = 1;
                    for(Expression arg:args) {
                        Integer v = arg.evaluate(s, code, silent);
                        if (v == null) return null;
                        accum *= v;
                    }
                    return accum;
                }

            case EXPRESSION_DIV:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1/v2;
                }

            case EXPRESSION_MOD:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 % v2;
                }

            case EXPRESSION_OR:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    boolean b1 = v1 != 0;
                    boolean b2 = v2 != 0;
                    return b1 || b2 ? TRUE : FALSE;
                }

            case EXPRESSION_AND:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    boolean b1 = v1 != 0;
                    boolean b2 = v2 != 0;
                    return b1 && b2 ? TRUE : FALSE;
                }

            case EXPRESSION_EQUAL:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    if (v1.equals(v2)) return TRUE;
                    return FALSE;
                }

            case EXPRESSION_LOWERTHAN:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    if (v1 < v2) return TRUE;
                    return FALSE;
                }

            case EXPRESSION_GREATERTHAN:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    if (v1 > v2) return TRUE;
                    return FALSE;
                }

            case EXPRESSION_LEQTHAN:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    if (v1 <= v2) return TRUE;
                    return FALSE;
                }

            case EXPRESSION_GEQTHAN:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    if (v1 >= v2) return TRUE;
                    return FALSE;
                }

            case EXPRESSION_DIFF:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    if (v1.equals(v2)) return FALSE;
                    return TRUE;
                }

            case EXPRESSION_TERNARY_IF:
                {
                    Integer cond = args.get(0).evaluate(s, code, silent);
                    if (cond == null) return null;
                    if (cond != FALSE) {
                        return args.get(1).evaluate(s, code, silent);
                    } else {
                        return args.get(2).evaluate(s, code, silent);
                    }
                }

            case EXPRESSION_LSHIFT:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 << v2;
                }

            case EXPRESSION_RSHIFT:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 >> v2;
                }

            case EXPRESSION_BITOR:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 | v2;
                }

            case EXPRESSION_BITAND:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 & v2;
                }
            case EXPRESSION_BITNEGATION:
                {
                    Integer v = args.get(0).evaluate(s, code, silent);
                    if (v == null) return null;
                    return ~v;
                }
            case EXPRESSION_BITXOR:
                {
                    if (args.size() != 2) return null;
                    Integer v1 = args.get(0).evaluate(s, code, silent);
                    Integer v2 = args.get(1).evaluate(s, code, silent);
                    if (v1 == null || v2 == null) return null;
                    return v1 ^ v2;
                }
            case EXPRESSION_LOGICAL_NEGATION:
                {
                    Integer v = args.get(0).evaluate(s, code, silent);
                    if (v == null) return null;
                    return v == FALSE ? TRUE:FALSE;
                }

        }

        return null;
    }


    public String toString()
    {
        switch(type) {
            case EXPRESSION_REGISTER_OR_FLAG:
                if (config.opsInLowerCase) {
                    return registerOrFlagName.toLowerCase();
                } else if (config.opsInUpperCase) {
                    return registerOrFlagName.toUpperCase();
                } else {
                    return registerOrFlagName;
                }
            case EXPRESSION_NUMERIC_CONSTANT: 
                return ""+numericConstant;
            case EXPRESSION_STRING_CONSTANT:
                return "\"" + stringConstant + "\"";
            case EXPRESSION_SYMBOL:
                return symbolName;
            case EXPRESSION_SIGN_CHANGE:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG ||
                    args.get(0).type == EXPRESSION_NUMERIC_CONSTANT ||
                    args.get(0).type == EXPRESSION_STRING_CONSTANT ||
                    args.get(0).type == EXPRESSION_PARENTHESIS ||
                    args.get(0).type == EXPRESSION_SYMBOL) {
                    return "-" + args.get(0).toString();
                } else {
                    return "-(" + args.get(0).toString() + ")";
                }
            case EXPRESSION_PARENTHESIS:
                return "(" + args.get(0).toString() + ")";
            case EXPRESSION_SUM:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " + " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_SUB:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " - " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_MUL:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " * " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_DIV:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " / " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_MOD:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " % " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_OR:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " || " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_AND:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " && " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_EQUAL:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " = " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_LOWERTHAN:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " < " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_GREATERTHAN:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " > " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_LEQTHAN:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " <= " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_GEQTHAN:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " >= " + arg.toString();
                        }
                    }
                    return str;
                }

            case EXPRESSION_DIFF:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " != " + arg.toString();
                        }
                    }
                    return str;
                }

            case EXPRESSION_TERNARY_IF:
                {
                    return args.get(0).toString() + " ? " +
                           args.get(1).toString() + " : " +
                           args.get(2).toString();
                }

            case EXPRESSION_LSHIFT:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " << " + arg.toString();
                        }
                    }
                    return str;
                }

            case EXPRESSION_RSHIFT:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " >> " + arg.toString();
                        }
                    }
                    return str;
                }

            case EXPRESSION_BITOR:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " | " + arg.toString();
                        }
                    }
                    return str;
                }

            case EXPRESSION_BITAND:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " & " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_BITNEGATION:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG ||
                    args.get(0).type == EXPRESSION_NUMERIC_CONSTANT ||
                    args.get(0).type == EXPRESSION_STRING_CONSTANT ||
                    args.get(0).type == EXPRESSION_PARENTHESIS ||
                    args.get(0).type == EXPRESSION_SYMBOL) {
                    return "~" + args.get(0).toString();
                } else {
                    return "~(" + args.get(0).toString() + ")";
                }
            case EXPRESSION_BITXOR:
                {
                    String str = null;
                    for(Expression arg:args) {
                        if (str == null) {
                            str = arg.toString();
                        } else {
                            str += " ^ " + arg.toString();
                        }
                    }
                    return str;
                }
            case EXPRESSION_LOGICAL_NEGATION:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG ||
                    args.get(0).type == EXPRESSION_NUMERIC_CONSTANT ||
                    args.get(0).type == EXPRESSION_STRING_CONSTANT ||
                    args.get(0).type == EXPRESSION_PARENTHESIS ||
                    args.get(0).type == EXPRESSION_SYMBOL) {
                    return "!" + args.get(0).toString();
                } else {
                    return "!(" + args.get(0).toString() + ")";
                }
            default:
                return "<UNSUPPORTED TYPE "+type+">";
        }
    }


    public boolean isRegister(CodeBase code)
    {
        if (type != EXPRESSION_REGISTER_OR_FLAG) return false;
        return code.isRegister(registerOrFlagName);
    }


    public boolean isConstant()
    {
        return (type == EXPRESSION_NUMERIC_CONSTANT) ||
               (type == EXPRESSION_STRING_CONSTANT);
    }


    public boolean evaluatesToNumericConstant()
    {
        if (type == EXPRESSION_NUMERIC_CONSTANT) return true;
        if (type == EXPRESSION_SYMBOL) return true;
        if (type == EXPRESSION_STRING_CONSTANT &&
            stringConstant.length() == 1) return true;
        if (type == EXPRESSION_SIGN_CHANGE ||
            type == EXPRESSION_PARENTHESIS ||
            type == EXPRESSION_SUM ||
            type == EXPRESSION_SUB ||
            type == EXPRESSION_MUL ||
            type == EXPRESSION_DIV ||
            type == EXPRESSION_MOD ||
            type == EXPRESSION_OR ||
            type == EXPRESSION_AND ||
            type == EXPRESSION_EQUAL ||
            type == EXPRESSION_LOWERTHAN ||
            type == EXPRESSION_GREATERTHAN ||
            type == EXPRESSION_LEQTHAN ||
            type == EXPRESSION_GEQTHAN ||
            type == EXPRESSION_DIFF ||
            type == EXPRESSION_LSHIFT ||
            type == EXPRESSION_RSHIFT ||
            type == EXPRESSION_BITOR ||
            type == EXPRESSION_BITAND ||
            type == EXPRESSION_BITNEGATION ||
            type == EXPRESSION_BITXOR ||
            type == EXPRESSION_LOGICAL_NEGATION) {
            for(Expression arg:args) {
                if (!arg.evaluatesToNumericConstant()) return false;
            }
            return true;
        }
        if (type == EXPRESSION_TERNARY_IF) {
            return args.get(1).evaluatesToNumericConstant() && args.get(2).evaluatesToNumericConstant();
        }
        return false;
    }


    public int sizeInBytes(int granularity)
    {
        if (type == EXPRESSION_STRING_CONSTANT) {
            return stringConstant.length();
        } else {
            return granularity;
        }
    }
    
    
    public static Expression constantExpression(int v, MDLConfig config)
    {
        Expression exp = new Expression(EXPRESSION_NUMERIC_CONSTANT, config);
        exp.numericConstant = v;
        return exp;
    }


    public static Expression constantExpression(String v, MDLConfig config)
    {
        Expression exp = new Expression(EXPRESSION_STRING_CONSTANT, config);
        exp.stringConstant = v;
        return exp;
    }


    public static Expression symbolExpression(String symbol, CodeBase code, MDLConfig config)
    {
        if (code.isRegister(symbol) ||
            code.isCondition(symbol)) {
            Expression exp = new Expression(EXPRESSION_REGISTER_OR_FLAG, config);
            exp.registerOrFlagName = symbol;
            return exp;
        } else {
            Expression exp = new Expression(EXPRESSION_SYMBOL, config);
            exp.symbolName = symbol;
            return exp;
        }
    }

    
    public static Expression signChangeExpression(Expression arg, MDLConfig config)
    {
        Expression exp = new Expression(EXPRESSION_SIGN_CHANGE, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    
    public static Expression bitNegationExpression(Expression arg, MDLConfig config)
    {
        Expression exp = new Expression(EXPRESSION_BITNEGATION, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }


    public static Expression parenthesisExpression(Expression arg, MDLConfig config)
    {
        Expression exp = new Expression(EXPRESSION_PARENTHESIS, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }


    public static Expression operatorExpression(int operator, Expression arg, MDLConfig config)
    {
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }


    public static Expression operatorExpression(int operator, Expression arg1, Expression arg2, MDLConfig config)
    {
        // look at operator precedence:
        if (OPERATOR_PRECEDENCE[operator] < 0) {
            config.error("Precedence for operator "+operator+" is undefined!");
            return null;
        }
        if (OPERATOR_PRECEDENCE[arg1.type] >= 0 &&
            OPERATOR_PRECEDENCE[operator] < OPERATOR_PRECEDENCE[arg1.type]) {
            // operator has higher precedence than the one in arg1, we need to reorder!
            if (OPERATOR_PRECEDENCE[arg2.type] >= 0 &&
                OPERATOR_PRECEDENCE[operator] < OPERATOR_PRECEDENCE[arg2.type]) {
                if (OPERATOR_PRECEDENCE[arg1.type] < OPERATOR_PRECEDENCE[arg2.type]) {
                    // (1 arg1 (2 operator 3)) arg2 4
                    Expression exp = new Expression(operator, config);
                    exp.args = new ArrayList<>();
                    exp.args.add(arg1.args.get(arg1.args.size()-1));
                    exp.args.add(arg2.args.get(0));
                    arg1.args.set(arg1.args.size()-1, exp);
                    arg2.args.set(0, arg1);
                    return arg2;
                } else {
                    // 1 arg1 ((2 operator 3) arg2 4)
                    Expression exp = new Expression(operator, config);
                    exp.args = new ArrayList<>();
                    exp.args.add(arg1.args.get(arg1.args.size()-1));
                    exp.args.add(arg2.args.get(0));
                    arg2.args.set(0, exp);
                    arg1.args.set(arg1.args.size()-1, arg2);
                    return arg1;
                }
            } else {
                // 1 arg1 (2 operator arg2)
                Expression exp = new Expression(operator, config);
                exp.args = new ArrayList<>();
                exp.args.add(arg1.args.get(arg1.args.size()-1));
                exp.args.add(arg2);
                arg1.args.set(arg1.args.size()-1, exp);
                return arg1;
            }
        } else if (OPERATOR_PRECEDENCE[arg2.type] >= 0 &&
                   OPERATOR_PRECEDENCE[operator] < OPERATOR_PRECEDENCE[arg2.type]) {
            // operator has higher precedence than the one in arg2, we need to reorder!
            // (arg1 operator 3) arg2 4
            Expression exp = new Expression(operator, config);
            exp.args = new ArrayList<>();
            exp.args.add(arg1);
            exp.args.add(arg2.args.get(0));
            arg2.args.set(0, exp);
            return arg2;
        }
        
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg1);
        exp.args.add(arg2);
        return exp;
    }


    public static Expression operatorTernaryExpression(int operator, Expression arg1, Expression arg2, Expression arg3, MDLConfig config)
    {
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg1);
        exp.args.add(arg2);
        exp.args.add(arg3);
        return exp;
    }
}

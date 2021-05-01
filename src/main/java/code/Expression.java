/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import org.apache.commons.lang3.tuple.Pair;

public class Expression {

    public static final int TRUE = -1;
    public static final int FALSE = 0;

    public static final int EXPRESSION_REGISTER_OR_FLAG = 0;
    public static final int EXPRESSION_INTEGER_CONSTANT = 1;
    public static final int EXPRESSION_STRING_CONSTANT = 2;
    public static final int EXPRESSION_DOUBLE_CONSTANT = 28;
    public static final int EXPRESSION_SYMBOL = 3;
    public static final int EXPRESSION_SIGN_CHANGE = 4;  // unary "-"
    public static final int EXPRESSION_PARENTHESIS = 5;
    public static final int EXPRESSION_SUM = 6;
    public static final int EXPRESSION_SUB = 7;
    public static final int EXPRESSION_MUL = 8;
    public static final int EXPRESSION_DIV = 9;
    public static final int EXPRESSION_MOD = 10;
    public static final int EXPRESSION_OR = 11;
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
    public static final int EXPRESSION_DIALECT_FUNCTION = 27;
    public static final int EXPRESSION_PLUS_SIGN = 29;  // just something like: +1, or +(3-5)
    public static final int EXPRESSION_LIST = 30;
    
    public static final int RENDER_DEFAULT = 0;
    public static final int RENDER_AS_8BITHEX = 1;
    public static final int RENDER_AS_16BITHEX = 2;
    public static final int RENDER_AS_OCT = 3;
    public static final int RENDER_AS_8BITBIN = 4;
    public static final int RENDER_AS_16BITBIN = 5;

    MDLConfig config;
    public int type;
    public int integerConstant;
    public int renderMode = RENDER_DEFAULT; // render as decimal, hex, binary, etc.
    public double doubleConstant;
    public String stringConstant;
    public String symbolName;
    public boolean symbolIsExternal;
    public String registerOrFlagName;
    public String parenthesis;  // whether the parenthesis is "(", "[", or "{"
    public String dialectFunction;
    public Expression originalDialectExpression = null; // if the expression was translated, we preserve the original
    public List<Expression> args = null;

    private Expression(int a_type, MDLConfig a_config) {
        type = a_type;
        config = a_config;
    }

    
    public void copyFrom(Expression e)
    {
        config = e.config;
        type = e.type;
        integerConstant = e.integerConstant;
        renderMode = e.renderMode;
        doubleConstant = e.doubleConstant;
        stringConstant = e.stringConstant;
        symbolName = e.symbolName;
        symbolIsExternal = e.symbolIsExternal;
        registerOrFlagName = e.registerOrFlagName;
        parenthesis = e.parenthesis;
        dialectFunction = e.dialectFunction;
        originalDialectExpression = e.originalDialectExpression;
        if (e.args != null) {
            args = new ArrayList<>();
            args.addAll(e.args);
        }        
    }


    @Override
    public Expression clone()
    {
        Expression e = new Expression(type, config);
        e.copyFrom(this);
        
        return e;
    }


    public Integer evaluateToInteger(CodeStatement s, CodeBase code, boolean silent) {
        Object v = evaluateInternal(s, code, silent, null, new ArrayList<>());
        if (v instanceof Integer) {
            return (Integer)v;
        } else if (v instanceof String) {
            String str = (String)v;
            if (str.length() == 2) {
                return str.charAt(0)*256 + str.charAt(1);
            }
            return null;
        }
        return null;
    }
   
    
    /*
    - previous only needs to be specified if this is called on a CodeStatement not yet added to a source file
      (for example, when parsing a macro being expanded), so that we know which will be the previous statement once it is added
    */
    public Integer evaluateToInteger(CodeStatement s, CodeBase code, boolean silent, CodeStatement previous) {
        Object v = evaluateInternal(s, code, silent, previous, new ArrayList<>());
        if (v instanceof Integer) {
            return (Integer)v;
        } else if (v instanceof String) {
            String str = (String)v;
            if (str.length() == 2) {
                return str.charAt(0)*256 + str.charAt(1);
            }
            return null;
        }
        return null;        
    }


    /*
    - previous only needs to be specified if this is called on a CodeStatement not yet added to a source file
      (for example, when parsing a macro being expanded), so that we know which will be the previous statement once it is added
    */
    public Integer evaluateToIntegerInternal(CodeStatement s, CodeBase code, boolean silent, CodeStatement previous, List<String> variableStack) {
        Object v = evaluateInternal(s, code, silent, previous, variableStack);
        if (v instanceof Integer) {
            return (Integer)v;
        } else if (v instanceof String) {
            String str = (String)v;
            if (str.length() == 2) {
                return str.charAt(0)*256 + str.charAt(1);
            }
            return null;
        }
        return null;
    }

    public String evaluateToString(CodeStatement s, CodeBase code, boolean silent) {
        return (String)evaluateInternal(s, code, silent, null, new ArrayList<>());
    }
    

    public Object evaluate(CodeStatement s, CodeBase code, boolean silent) {
        return evaluateInternal(s, code, silent, null, new ArrayList<>());
    }
    
    
    /*
    - previous only needs to be specified if this is called on a CodeStatement not yet added to a source file
      (for example, when parsing a macro being expanded), so that we know which will be the previous statement once it is added
    */
    public Object evaluateInternal(CodeStatement s, CodeBase code, boolean silent, CodeStatement previous, List<String> variableStack) {
        switch (type) {
            case EXPRESSION_INTEGER_CONSTANT:
                return integerConstant;

            case EXPRESSION_DOUBLE_CONSTANT:
                return doubleConstant;
                
            case EXPRESSION_STRING_CONSTANT:
                if (stringConstant.length() == 1) {
                    return (int) stringConstant.charAt(0);
                } else {
                    return stringConstant;
                }

            case EXPRESSION_LIST:
            {
                List<Object> l = new ArrayList<>();
                for(Expression exp:args) {
                    Object v = exp.evaluateInternal(s, code, silent, previous, variableStack);
                    if (v == null) return null;
                    l.add(v);
                }
                return l;
            }
                
            case EXPRESSION_SYMBOL: {
                if (symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
                    if (s != null) {
                        return s.getAddressInternal(code, true, previous, variableStack);
                    } else {
                        return null;
                    }
                }
                Object value = code.getSymbolValueInternal(symbolName, silent, variableStack);
                if (value == null) {
                    if (s != null && s.labelPrefix != null && !s.labelPrefix.isEmpty()) {
                        value = code.getSymbolValueInternal(s.labelPrefix + symbolName, silent, variableStack);
                    } else if (previous != null && previous.labelPrefix != null && !previous.labelPrefix.isEmpty()) {
                        value = code.getSymbolValueInternal(previous.labelPrefix + symbolName, silent, variableStack);
                    }
                    if (value == null) {
                            if (!silent) {
                            config.error("Undefined symbol " + symbolName + " in " + s.sl);
                        }
                        return null;
                    }
                }
                return value;
            }

            case EXPRESSION_SIGN_CHANGE: {
                Object v = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                if (v == null) {
                    return null;
                } else if (v instanceof Integer) {
                    return -(Integer)v;
                } else if (v instanceof Double) {
                    return -(Double)v;
                } else {
                    return null;
                }
            }

            case EXPRESSION_PARENTHESIS: {
                Object v = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                if (v == null) {
                    return null;
                }
                return v;
            }

            case EXPRESSION_SUM: {
                Number accum = 0;
                boolean turnToDouble = false;
                for (Expression arg : args) {
                    Object v = arg.evaluateInternal(s, code, silent, previous, variableStack);
                    if (v == null) {
                        return null;
                    } else if (v instanceof Double) {
                        turnToDouble = true;
                        accum = accum.doubleValue() + (Double)v;
                    } else if (v instanceof Integer) {
                        if (turnToDouble) {
                            accum = accum.doubleValue() + (Integer)v;
                        } else {
                            accum = accum.intValue() + (Integer)v;
                        }
                    } else {
                        return null;
                    }
                }
                return accum;
            }

            case EXPRESSION_SUB: {
                if (args.size() != 2) {
                    return null;
                }
                
                // - special case for when these are labels, like: label1-label2,
                // and it is not possible to assign an absolute value to the labels,
                // but it is possible to know their difference:
                if (args.get(0).type == Expression.EXPRESSION_SYMBOL &&
                    args.get(1).type == Expression.EXPRESSION_SYMBOL) {
                    SourceConstant c1 = code.getSymbol(args.get(0).symbolName);
                    SourceConstant c2 = code.getSymbol(args.get(1).symbolName);
                    if (c1 != null && c2 != null && c1.exp != null && c2.exp != null &&
                        c1.exp.type == Expression.EXPRESSION_SYMBOL &&
                        c2.exp.type == Expression.EXPRESSION_SYMBOL &&
                        c1.exp.symbolName.equals(CodeBase.CURRENT_ADDRESS) &&
                        c2.exp.symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
                        CodeStatement d1 = c1.definingStatement;
                        CodeStatement d2 = c2.definingStatement;
                        if (d1 != null && d2 != null && d1.source == d2.source) {
                            int idx1 = d1.source.getStatements().indexOf(d1);
                            int idx2 = d1.source.getStatements().indexOf(d2);
                            if (idx1 >= 0 && idx2 >= 0 && idx1 >= idx2) {
                                Integer diff = 0;
                                for(int i = idx2; i<idx1;i++) {
                                    CodeStatement si = d1.source.getStatements().get(i);
                                    if (si.type == CodeStatement.STATEMENT_ORG) {
                                        // stop! this method will not work if there is an org!
                                        diff = null;
                                        break;
                                    }
                                    Integer size = si.sizeInBytesInternal(code, true, true, true, variableStack);
                                    if (size == null) {
                                        diff = null;
                                        break;
                                    }
                                    diff += size;
                                }
                                if (diff != null) {
                                    return diff;
                                }
                            }
                        }
                    }
                }                    
                
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 - (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 - (Integer)v2;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 - (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 - (Integer)v2;
                    }
                }
                return null;
            }

            case EXPRESSION_MUL: {
                Number accum = 1;
                boolean turnToDouble = false;
                for (Expression arg : args) {
                    Object v = arg.evaluateInternal(s, code, silent, previous, variableStack);
                    if (v == null) {
                        return null;
                    } else if (v instanceof Double) {
                        turnToDouble = true;
                        accum = accum.doubleValue() * (Double)v;
                    } else if (v instanceof Integer) {
                        if (turnToDouble) {
                            accum = accum.doubleValue() * (Integer)v;
                        } else {
                            accum = accum.intValue() * (Integer)v;
                        }
                    } else {
                        return null;
                    }
                }
                return accum;
            }

            case EXPRESSION_DIV: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 / (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 / (Integer)v2;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 / (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 / (Integer)v2;
                    }
                }
            }

            case EXPRESSION_MOD: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 % (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 % (Integer)v2;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 % (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 % (Integer)v2;
                    }
                }
            }

            case EXPRESSION_OR: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                boolean b1 = v1 != 0;
                boolean b2 = v2 != 0;
                return b1 || b2 ? TRUE : FALSE;
            }

            case EXPRESSION_AND: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                boolean b1 = v1 != 0;
                boolean b2 = v2 != 0;
                return b1 && b2 ? TRUE : FALSE;
            }

            case EXPRESSION_EQUAL: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if (v1.equals(v2)) {
                    return TRUE;
                }
                return FALSE;
            }

            case EXPRESSION_LOWERTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 < (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 < (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 < (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 < (Integer)v2 ? TRUE:FALSE;
                    }
                }
            }

            case EXPRESSION_GREATERTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 > (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 > (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 > (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 > (Integer)v2 ? TRUE:FALSE;
                    }
                }            }

            case EXPRESSION_LEQTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 <= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 <= (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 <= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 <= (Integer)v2 ? TRUE:FALSE;
                    }
                }            
            }

            case EXPRESSION_GEQTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 >= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 >= (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 >= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 >= (Integer)v2 ? TRUE:FALSE;
                    }
                }            
            }

            case EXPRESSION_DIFF: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, previous, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, previous, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if (v1.equals(v2)) {
                    return FALSE;
                }
                return TRUE;
            }

            case EXPRESSION_TERNARY_IF: {
                Integer cond = args.get(0).evaluateToInteger(s, code, silent, previous);
                if (cond == null) {
                    return null;
                }
                if (cond != FALSE) {
                    return args.get(1).evaluateToInteger(s, code, silent, previous);
                } else {
                    return args.get(2).evaluateToInteger(s, code, silent, previous);
                }
            }

            case EXPRESSION_LSHIFT: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 << v2;
            }

            case EXPRESSION_RSHIFT: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 >> v2;
            }

            case EXPRESSION_BITOR: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 | v2;
            }

            case EXPRESSION_BITAND: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 & v2;
            }
            case EXPRESSION_BITNEGATION: {
                Integer v = args.get(0).evaluateToInteger(s, code, silent, previous);
                if (v == null) {
                    return null;
                }
                return ~v;
            }
            case EXPRESSION_BITXOR: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent, previous);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent, previous);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 ^ v2;
            }
            case EXPRESSION_LOGICAL_NEGATION: {
                Integer v = args.get(0).evaluateToInteger(s, code, silent, previous);
                if (v == null) {
                    return null;
                }
                return v == FALSE ? TRUE : FALSE;
            }
            case EXPRESSION_DIALECT_FUNCTION: {
                return config.dialectParser.evaluateExpression(dialectFunction, args, s, code, silent);
            }
            case EXPRESSION_PLUS_SIGN:
                return args.get(0).evaluateInternal(s, code, silent, previous, variableStack);

        }

        return null;
    }

    
    @Override
    public String toString() {
        return toStringInternal(false, false, false, null, null);
    }    
    

    public String toStringInternal(boolean splitSpecialCharactersInStrings, boolean useOriginalNames, boolean mimicTargetDialect, CodeStatement s, CodeBase code) {
        if (mimicTargetDialect && originalDialectExpression != null) {
            return originalDialectExpression.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
        }
        
        switch (type) {
            case EXPRESSION_REGISTER_OR_FLAG:
                if (config.output_opsInLowerCase) {
                    return registerOrFlagName.toLowerCase();
                } else if (config.output_opsInUpperCase) {
                    return registerOrFlagName.toUpperCase();
                } else {
                    return registerOrFlagName;
                }
            case EXPRESSION_INTEGER_CONSTANT:
                switch(renderMode) {
                    case RENDER_AS_8BITHEX:
                        if (integerConstant >= 0 && integerConstant <= 0xff) {
                            return config.tokenizer.toHexByte(integerConstant, config.hexStyle);
                        }
                        break;
                    case RENDER_AS_16BITHEX:
                        if (integerConstant >= 0 && integerConstant <= 0xffff) {
                            return config.tokenizer.toHexWord(integerConstant, config.hexStyle);
                        }
                        break;
                    case RENDER_AS_OCT:
                        return config.tokenizer.toOct(integerConstant, config.hexStyle);
                    case RENDER_AS_8BITBIN:
                        if (integerConstant >= 0 && integerConstant <= 0xff) {
                            return config.tokenizer.toBin(integerConstant, 8, config.hexStyle);
                        }
                        break;
                    case RENDER_AS_16BITBIN:
                        if (integerConstant >= 0 && integerConstant <= 0xffff) {
                            return config.tokenizer.toBin(integerConstant, 16, config.hexStyle);
                        }
                        break;
                }
                return "" + integerConstant;
            case EXPRESSION_DOUBLE_CONSTANT:
                return "" + doubleConstant;
            case EXPRESSION_STRING_CONSTANT:
            {
                char quoteChar = '\"';
                // We switch to ' in strings of length one, since some assemblers do not
                // like expressions like: 1 + "A"
                if (config.useSingleQotesForsingleCharStrings && stringConstant.length() == 1) quoteChar = '\'';
                if (splitSpecialCharactersInStrings) {
                    String tmp = "";
                    boolean first = true;
                    boolean insideQuotes = false;
                    for(int i = 0;i<stringConstant.length();i++) {
                        int c = stringConstant.charAt(i);
                        if (c<32 || c=='\\' || c=='\"') {
                            if (insideQuotes) {
                                tmp += quoteChar;
                                insideQuotes = false;
                            }
                            tmp += (first ? "":", ") + c;
                        } else {
                            if (insideQuotes) {
                                tmp += stringConstant.substring(i,i+1);
                            } else {
                                tmp += (first ? "":", ") + "\"" + stringConstant.substring(i,i+1);
                                insideQuotes = true;
                            }
                        }
                        first = false;
                    }
                    if (insideQuotes) tmp += quoteChar;
                    return tmp;
                } else {
                    return quoteChar + stringConstant + quoteChar;
                }
            }
            case EXPRESSION_SYMBOL:
            {
                if (useOriginalNames && code != null) {
                    SourceConstant c = code.getSymbol(symbolName);
                    if (c != null) {
                        String suffix = "";
                        if (mimicTargetDialect && symbolIsExternal && config.expressionParser.doubleHashToMarkExternalSymbols) {
                            suffix = "##";
                        }
                        if (config.output_replaceLabelDotsByUnderscores && !c.originalName.startsWith(".")) {
                            return c.originalName.replace(".", "_") + suffix;
                        } else {
                            return c.originalName + suffix;
                        }
                    }
                }
                if (config.output_replaceLabelDotsByUnderscores) {
                    return symbolName.replace(".", "_");
                } else {
                    return symbolName;
                }
            }
            case EXPRESSION_SIGN_CHANGE:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG
                        || args.get(0).type == EXPRESSION_INTEGER_CONSTANT
                        || args.get(0).type == EXPRESSION_STRING_CONSTANT
                        || args.get(0).type == EXPRESSION_PARENTHESIS
                        || args.get(0).type == EXPRESSION_SYMBOL) {
                    return "-" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                } else {
                    return "-(" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + ")";
                }
            case EXPRESSION_PARENTHESIS:
                return "(" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + ")";
            case EXPRESSION_SUM: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        str += " + " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                }
                return str;
            }
            case EXPRESSION_SUB: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        str += " - " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                }
                return str;
            }
            case EXPRESSION_MUL: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        str += " * " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                }
                return str;
            }
            case EXPRESSION_DIV: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        str += " / " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                }
                return str;
            }
            case EXPRESSION_MOD: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_MOD+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_MOD+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_OR: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_BIT_OR+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_BIT_OR+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_AND: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_BIT_AND+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_BIT_AND+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_EQUAL: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_EQUAL+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_EQUAL+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_LOWERTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_LOWERTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_LOWERTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_GREATERTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_GREATERTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_GREATERTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_LEQTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_LEQTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_LEQTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_GEQTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_GEQTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_GEQTHAN+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }

            case EXPRESSION_DIFF: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_DIFF+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_DIFF+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }

            case EXPRESSION_TERNARY_IF: {
                return args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + " ? "
                        + args.get(1).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + " : "
                        + args.get(2).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
            }

            case EXPRESSION_LSHIFT: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_LSHIFT+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_LSHIFT+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }

            case EXPRESSION_RSHIFT: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_RSHIFT+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_RSHIFT+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }

            case EXPRESSION_BITOR: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_BIT_OR+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_BIT_OR+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                    
                }
                return str;
            }

            case EXPRESSION_BITAND: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_BIT_AND+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_BIT_AND+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_BITNEGATION:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG
                        || args.get(0).type == EXPRESSION_INTEGER_CONSTANT
                        || args.get(0).type == EXPRESSION_STRING_CONSTANT
                        || args.get(0).type == EXPRESSION_PARENTHESIS
                        || args.get(0).type == EXPRESSION_SYMBOL) {
                    if (mimicTargetDialect) {
                        return config.expressionParser.OP_BIT_NEGATION +" " + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        return config.expressionParser.OP_STD_BIT_NEGATION + " " + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                } else {
                    if (mimicTargetDialect) {
                        return config.expressionParser.OP_BIT_NEGATION + " (" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + ")";
                    } else {
                        return config.expressionParser.OP_STD_BIT_NEGATION + " (" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + ")";
                    }
                }
            case EXPRESSION_BITXOR: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        if (mimicTargetDialect) {
                            str += " "+config.expressionParser.OP_BIT_XOR+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        } else {
                            str += " "+config.expressionParser.OP_STD_BIT_XOR+" " + arg.toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                        }
                    }
                }
                return str;
            }
            case EXPRESSION_LOGICAL_NEGATION:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG
                        || args.get(0).type == EXPRESSION_INTEGER_CONSTANT
                        || args.get(0).type == EXPRESSION_STRING_CONSTANT
                        || args.get(0).type == EXPRESSION_PARENTHESIS
                        || args.get(0).type == EXPRESSION_SYMBOL) {
                    if (mimicTargetDialect) {
                        return config.expressionParser.OP_LOGICAL_NEGATION+" " + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        return config.expressionParser.OP_STD_LOGICAL_NEGATION+" " + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                } else {
                    if (mimicTargetDialect) {
                        return config.expressionParser.OP_LOGICAL_NEGATION+" (" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + ")";
                    } else {
                        return config.expressionParser.OP_STD_LOGICAL_NEGATION+" (" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code) + ")";
                    }
                }
            case EXPRESSION_DIALECT_FUNCTION:
            {
                boolean parentheses = true;
                if (config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.containsKey(dialectFunction)) {
                    parentheses = false;
                }
                String str = dialectFunction + (parentheses ? "(":" ");
                for(int i = 0;i<args.size();i++) {
                    if (i == 0) {
                        str += args.get(i).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        str += ", " + args.get(i).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                }
                return str += (parentheses ? ")":"");
            }
            case EXPRESSION_PLUS_SIGN:
            {
                return "+" + args.get(0).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
            }
            case EXPRESSION_LIST:
            {
                String str = "";
                for(int i = 0;i<args.size();i++) {
                    if (i == 0) {
                        str += args.get(i).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    } else {
                        str += ", " + args.get(i).toStringInternal(splitSpecialCharactersInStrings, useOriginalNames, mimicTargetDialect, s, code);
                    }
                }
                return str;
            }
            default:
                return "<UNSUPPORTED TYPE " + type + ">";
        }
    }

    public boolean isRegister(CodeBase code) {
        if (type != EXPRESSION_REGISTER_OR_FLAG) {
            return false;
        }
        return code.isRegister(registerOrFlagName);
    }

    public boolean isConstant() {
        return (type == EXPRESSION_INTEGER_CONSTANT)
                || (type == EXPRESSION_DOUBLE_CONSTANT)
                || (type == EXPRESSION_STRING_CONSTANT);
    }

    public boolean evaluatesToNumericConstant() {
        if (type == EXPRESSION_INTEGER_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_DOUBLE_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_SYMBOL) {
            return true;
        }
        if (type == EXPRESSION_STRING_CONSTANT
                && (stringConstant.length() == 1 || 
                    stringConstant.length() == 2)) {
            return true;
        }
        if (type == EXPRESSION_SIGN_CHANGE
                || type == EXPRESSION_PARENTHESIS
                || type == EXPRESSION_SUM
                || type == EXPRESSION_SUB
                || type == EXPRESSION_MUL
                || type == EXPRESSION_DIV
                || type == EXPRESSION_MOD
                || type == EXPRESSION_OR
                || type == EXPRESSION_AND
                || type == EXPRESSION_EQUAL
                || type == EXPRESSION_LOWERTHAN
                || type == EXPRESSION_GREATERTHAN
                || type == EXPRESSION_LEQTHAN
                || type == EXPRESSION_GEQTHAN
                || type == EXPRESSION_DIFF
                || type == EXPRESSION_LSHIFT
                || type == EXPRESSION_RSHIFT
                || type == EXPRESSION_BITOR
                || type == EXPRESSION_BITAND
                || type == EXPRESSION_BITNEGATION
                || type == EXPRESSION_BITXOR
                || type == EXPRESSION_LOGICAL_NEGATION
                || type == EXPRESSION_PLUS_SIGN) {
            for (Expression arg : args) {
                if (!arg.evaluatesToNumericConstant()) {
                    return false;
                }
            }
            return true;
        }
        if (type == EXPRESSION_TERNARY_IF) {
            return args.get(1).evaluatesToIntegerConstant() && args.get(2).evaluatesToIntegerConstant();
        }
        if (type == EXPRESSION_DIALECT_FUNCTION) {
            return config.dialectParser.expressionEvaluatesToIntegerConstant(dialectFunction);
        }
        return false;
    }    
    
    
    public boolean evaluatesToIntegerConstant() {
        if (type == EXPRESSION_INTEGER_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_SYMBOL) {
            return true;
        }
        if (type == EXPRESSION_STRING_CONSTANT
                && (stringConstant.length() == 1 || 
                    stringConstant.length() == 2)) {
            return true;
        }
        if (type == EXPRESSION_SIGN_CHANGE
                || type == EXPRESSION_PARENTHESIS
                || type == EXPRESSION_SUM
                || type == EXPRESSION_SUB
                || type == EXPRESSION_MUL
                || type == EXPRESSION_DIV
                || type == EXPRESSION_MOD
                || type == EXPRESSION_OR
                || type == EXPRESSION_AND
                || type == EXPRESSION_EQUAL
                || type == EXPRESSION_LOWERTHAN
                || type == EXPRESSION_GREATERTHAN
                || type == EXPRESSION_LEQTHAN
                || type == EXPRESSION_GEQTHAN
                || type == EXPRESSION_DIFF
                || type == EXPRESSION_LSHIFT
                || type == EXPRESSION_RSHIFT
                || type == EXPRESSION_BITOR
                || type == EXPRESSION_BITAND
                || type == EXPRESSION_BITNEGATION
                || type == EXPRESSION_BITXOR
                || type == EXPRESSION_LOGICAL_NEGATION
                || type == EXPRESSION_PLUS_SIGN) {
            for (Expression arg : args) {
                if (!arg.evaluatesToIntegerConstant()) {
                    return false;
                }
            }
            return true;
        }
        if (type == EXPRESSION_TERNARY_IF) {
            return args.get(1).evaluatesToIntegerConstant() && args.get(2).evaluatesToIntegerConstant();
        }
        if (type == EXPRESSION_DIALECT_FUNCTION) {
            return config.dialectParser.expressionEvaluatesToIntegerConstant(dialectFunction);
        }
        return false;
    }
    
    public boolean evaluatesToStringConstant() {
        if (type == EXPRESSION_INTEGER_CONSTANT) {
            return false;
        }
        if (type == EXPRESSION_SYMBOL) {
            return false;
        }
        if (type == EXPRESSION_STRING_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_PARENTHESIS) {
            return args.get(0).evaluatesToStringConstant();
        }
        if (type == EXPRESSION_TERNARY_IF) {
            return args.get(1).evaluatesToStringConstant() && args.get(2).evaluatesToStringConstant();
        }
        if (type == EXPRESSION_DIALECT_FUNCTION) {
            return config.dialectParser.expressionEvaluatesToStringConstant(dialectFunction);
        }
        return false;        
    }

    public int sizeInBytes(int granularity) {
        switch (type) {
            case EXPRESSION_STRING_CONSTANT:
                return stringConstant.length();
            case EXPRESSION_LIST:
                int size = 0;
                for(Expression exp:args) {
                    size += exp.sizeInBytes(granularity);
                }
                return size;
            default:
                return granularity;
        }
    }
    
    public boolean resolveLocalLabels(String labelPrefix, CodeStatement s, CodeBase code)
    {
        if (type == EXPRESSION_SYMBOL) {
            if (symbolName.equals(CodeBase.CURRENT_ADDRESS)) return true;
            SourceConstant sc = code.getSymbol(labelPrefix + symbolName);
            if (sc != null) {
                symbolName = sc.name;
                return true;
            } else if (!labelPrefix.isEmpty()) {
                int idx = labelPrefix.substring(0,labelPrefix.length()-1).lastIndexOf(".");
                if (idx >= 0) {
                    return resolveLocalLabels(labelPrefix.substring(0, idx+1), s, code);
                }
            }
            return false;
        } else if (args != null) {
            boolean allResolved = true;
            for(Expression exp:args) {
                if (!exp.resolveLocalLabels(labelPrefix, s, code)) allResolved = false;
            }
            return allResolved;
        }
        return true;
    }
    
    
    public void resolveEagerSymbols(CodeBase code)
    {
        switch(type) {
            case EXPRESSION_SYMBOL:
                {
                    SourceConstant c = code.getSymbol(symbolName);
                    if (c != null && c.resolveEagerly && c.exp != null) {
                        Object value = c.exp.evaluate(c.definingStatement, code, true);
                        if (value != null) {
                            if (value instanceof Integer) {
                                this.type = EXPRESSION_INTEGER_CONSTANT;
                                this.integerConstant = (Integer)value;
                                this.symbolName = null;
                            } else if (value instanceof Double) {
                                this.type = EXPRESSION_DOUBLE_CONSTANT;
                                this.doubleConstant = (Double)value;
                                this.symbolName = null;
                            } else if (value instanceof String) {
                                this.type = EXPRESSION_STRING_CONSTANT;
                                this.stringConstant = (String)value;
                                this.symbolName = null;
                            } else if (value instanceof List) {
                                this.type = EXPRESSION_LIST;
                                this.args = (List<Expression>)value;
                                this.symbolName = null;
                            } else {
                                config.warn("resolveEagerSymbols: Unsupported expression evaluation type: " + value);
                            }
                        }
                    }
                }
            default:
                if (args != null) {
                    for(int i = 0;i<args.size();i++) {
                        args.get(i).resolveEagerSymbols(code);
                    }
                }
        }
    }
    
    
    public boolean containsLabel(CodeBase code)
    {
        if (type == EXPRESSION_SYMBOL) {
            SourceConstant sc = code.getSymbol(symbolName);
            if (sc != null) return sc.isLabel();
        } else if (args != null) {
            for(Expression arg:args) {
                if (arg.containsLabel(code)) return true;
            }                
        }
        return false;
    }
    
    
    public boolean containsSymbol()
    {
        if (type == EXPRESSION_SYMBOL) {
            return true;
        } else if (args != null) {
            for(Expression arg:args) {
                if (arg.containsSymbol()) return true;
            }                
        }
        return false;
    }    
    
    
    public boolean containsCurrentAddress()
    {
        if (type == EXPRESSION_SYMBOL &&
            symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
            return true;
        } else if (args != null) {
            for(Expression arg:args) {
                if (arg.containsCurrentAddress()) return true;
            }                
        }
        return false;
    }    


    public List<String> getAllSymbols()
    {
        List<String> l = new ArrayList<>();
        getAllSymbols(l);
        return l;
    }


    public void getAllSymbols(List<String> l)
    {
        if (type == EXPRESSION_SYMBOL) {
            if (!l.contains(symbolName)) l.add(symbolName);
        } else if (args != null) {
            for(Expression arg:args) {
                arg.getAllSymbols(l);
            }
        }
    }    
    
    
    public String findUndefinedSymbol(CodeBase code)
    {
        if (type == EXPRESSION_SYMBOL && !symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
            SourceConstant tmp = code.getSymbol(symbolName);
            if (tmp == null) return symbolName;
        } else if (args != null) {
            for(Expression arg:args) {
                String tmp = arg.findUndefinedSymbol(code);
                if (tmp != null) return tmp;
            }
        }
        return null;
    }
    
    
    public void parenthesizeIfNecessary()
    {
        for(int i = 0;i<args.size();i++) {
            Expression arg = args.get(i);
            if (config.expressionParser.STD_OPERATOR_PRECEDENCE[type] <
                config.expressionParser.STD_OPERATOR_PRECEDENCE[arg.type]) {
                args.set(i, Expression.parenthesisExpression(arg, config.expressionParser.default_parenthesis, config));
            }            
        }
    }
    

    public static Expression constantExpression(int v, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_INTEGER_CONSTANT, config);
        exp.integerConstant = v;
        return exp;
    }

    public static Expression constantExpression(int v, int renderMode, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_INTEGER_CONSTANT, config);
        exp.integerConstant = v;
        exp.renderMode = renderMode;
        return exp;
    }
    
    public static Expression constantExpression(double v, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_DOUBLE_CONSTANT, config);
        exp.doubleConstant = v;
        return exp;
    }

    public static Expression constantExpression(String v, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_STRING_CONSTANT, config);
        exp.stringConstant = v;
        return exp;
    }

    
    public static Expression symbolExpression(String symbol, CodeStatement s, CodeBase code, MDLConfig config) {
        return symbolExpressionInternal(symbol, s, code, true, config);
    }    
    
    
    public static Expression symbolExpressionInternal(String symbol, CodeStatement s, CodeBase code, boolean evaluateEagerSymbols, MDLConfig config) {
        if (config.expressionParser.registerSynonyms.containsKey(symbol)) {
            symbol = config.expressionParser.registerSynonyms.get(symbol);
        }
        if (code.isRegister(symbol) || code.isCondition(symbol)) {
            Expression exp = new Expression(EXPRESSION_REGISTER_OR_FLAG, config);
            exp.registerOrFlagName = symbol;
            return exp;
        } else {
            Expression exp = new Expression(EXPRESSION_SYMBOL, config);
            exp.symbolName = symbol;
            
            // check if it's a variable that needs to be evaluated eagerly:
            SourceConstant c = code.getSymbol(exp.symbolName);
            if (c == null && s != null && s.labelPrefix != null && !s.labelPrefix.isEmpty()) {
                c = code.getSymbol(s.labelPrefix+exp.symbolName);
            }
            if (c != null && c.resolveEagerly && evaluateEagerSymbols) {
                Object value = c.getValue(code, false);
                if (value == null) {
                    config.error("Cannot resolve eager variable " + symbol + "!");
                    return null;
                } 
                if (value instanceof Integer) {
                    exp = new Expression(EXPRESSION_INTEGER_CONSTANT, config);
                    exp.integerConstant = (Integer)value;
                } else if (value instanceof Double) {
                    exp = new Expression(EXPRESSION_DOUBLE_CONSTANT, config);
                    exp.doubleConstant = (Double)value;
                } else if (value instanceof String) {
                    exp = new Expression(EXPRESSION_STRING_CONSTANT, config);
                    exp.stringConstant = (String)value;
                } else if (value instanceof List) {
                    exp = Expression.listExpression((List<Expression>)value, config);
                } else {
                    return null;
                }
            }            
            return exp;
        }
    }
    
    
    public static Expression symbolExpressionInternal2(String symbol, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_SYMBOL, config);
        exp.symbolName = symbol;
        return exp;
    }
    

    public static Expression signChangeExpression(Expression arg, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_SIGN_CHANGE, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    public static Expression bitNegationExpression(Expression arg, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_BITNEGATION, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    public static Expression parenthesisExpression(Expression arg, String parenthesis, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_PARENTHESIS, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        exp.parenthesis = parenthesis;
        return exp;
    }
    
    public static Expression parenthesisExpressionIfNotConstant(Expression arg, String parenthesis, MDLConfig config) {
        if (arg.isConstant()) return arg;
        Expression exp = new Expression(EXPRESSION_PARENTHESIS, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        exp.parenthesis = parenthesis;
        return exp;
    }    

    public static Expression operatorExpression(int operator, Expression arg, MDLConfig config) {
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    public static Expression operatorExpression(int operator, Expression arg1, Expression arg2, MDLConfig config) {
        // look at operator precedence:
        if (config.expressionParser.OPERATOR_PRECEDENCE[operator] < 0) {
            config.error("Precedence for operator " + operator + " is undefined!");
            return null;
        }
        if (config.expressionParser.OPERATOR_PRECEDENCE[arg1.type] >= 0
                && config.expressionParser.OPERATOR_PRECEDENCE[operator] < config.expressionParser.OPERATOR_PRECEDENCE[arg1.type]) {
            // operator has higher precedence than the one in arg1, we need to reorder!
            if (config.expressionParser.OPERATOR_PRECEDENCE[arg2.type] >= 0
                    && config.expressionParser.OPERATOR_PRECEDENCE[operator] < config.expressionParser.OPERATOR_PRECEDENCE[arg2.type]) {
                if (config.expressionParser.OPERATOR_PRECEDENCE[arg1.type] < config.expressionParser.OPERATOR_PRECEDENCE[arg2.type]) {
                    // (1 arg1 (2 operator 3)) arg2 4
                    Expression exp = operatorExpression(operator, arg1.args.get(arg1.args.size() - 1), arg2.args.get(0), config);                    
                    arg1.args.set(arg1.args.size() - 1, exp);
                    arg2.args.set(0, arg1);
                    arg2.parenthesizeIfNecessary();
                    return arg2;
                } else {
                    // 1 arg1 ((2 operator 3) arg2 4)
                    Expression exp = operatorExpression(operator, arg1.args.get(arg1.args.size() - 1), arg2.args.get(0), config);
                    arg2.args.set(0, exp);
                    arg1.args.set(arg1.args.size() - 1, arg2);
                    arg1.parenthesizeIfNecessary();
                    return arg1;
                }
            } else {
                // 1 arg1 (2 operator arg2)
                Expression exp = operatorExpression(operator, arg1.args.get(arg1.args.size() - 1), arg2, config);
                arg1.args.set(arg1.args.size() - 1, exp);
                arg1.parenthesizeIfNecessary();
                return arg1;
            }
        } else if (config.expressionParser.OPERATOR_PRECEDENCE[arg2.type] >= 0
                && config.expressionParser.OPERATOR_PRECEDENCE[operator] < config.expressionParser.OPERATOR_PRECEDENCE[arg2.type]) {
            // operator has higher precedence than the one in arg2, we need to reorder!
            // (arg1 operator 3) arg2 4
            Expression exp = operatorExpression(operator, arg1, arg2.args.get(0), config);
            arg2.args.set(0, exp);
            arg2.parenthesizeIfNecessary();
            return arg2;
        }

        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg1);
        exp.args.add(arg2);
        exp.parenthesizeIfNecessary();
        return exp;
    }

    
    public static Expression operatorTernaryExpression(int operator, Expression arg1, Expression arg2, Expression arg3, MDLConfig config) {
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg1);
        exp.args.add(arg2);
        exp.args.add(arg3);
        return exp;
    }

    
    public static Expression dialectFunctionExpression(String functionName, List<Expression> a_args, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_DIALECT_FUNCTION, config);
        exp.dialectFunction = functionName;
        exp.args = new ArrayList<>();
        exp.args.addAll(a_args);
        return exp;
    }

    
    public static Expression dialectFunctionExpressionMaybeTranslated(String functionName, Expression arg, int precedence, CodeStatement s, CodeBase code, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_DIALECT_FUNCTION, config);
        exp.dialectFunction = functionName;
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        
        // reorganize based on precedence:
        int arg_precedence = config.expressionParser.OPERATOR_PRECEDENCE[arg.type];

        if (arg.args != null && arg.args.size() == 2 &&
            arg_precedence >= precedence) {
            // we need to reorganize!
            Expression exp2 = dialectFunctionExpressionMaybeTranslated(functionName, arg.args.get(0), precedence, s, code, config);

            List<Expression> arg_args = arg.args;
            arg.args = new ArrayList<>();
            arg.args.add(Expression.parenthesisExpression(exp2, config.expressionParser.default_parenthesis, config));
            arg.args.add(arg_args.get(1));
            exp = arg;
        } else {
            // try to translate it:
            Expression translated = config.dialectParser.translateToStandardExpression(functionName, exp.args, s, code);
            if (translated != null) {
                translated.originalDialectExpression = exp;
                exp = translated;
            } else {
                if (config.evaluateDialectFunctions) {
                    config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                }
            }
        }
        
        return exp;
    }

    
    public static Expression listExpression(List<? extends Object> a_args, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_LIST, config);
        exp.args = genericListToExpressionList(a_args, config);
        return exp;
    }

    
    public static List<Expression> genericListToExpressionList(List<? extends Object> gl, MDLConfig config) {
        List<Expression> el = new ArrayList<>();
        
        for(Object o:gl) {
            if (o instanceof Expression) {
                el.add((Expression)o);
            } else if (o instanceof Integer) {
                el.add(Expression.constantExpression((Integer)o, config));
            } else if (o instanceof Double) {
                el.add(Expression.constantExpression((Double)o, config));
            } else if (o instanceof String) {
                el.add(Expression.constantExpression((String)o, config));
            } else if (o instanceof List) {
                el.addAll(genericListToExpressionList((List<? extends Object>)o, config));
            } else {
                config.error("Unsupported value " + o + " creating a list expression!");
            }
        }   
        
        return el;
    }
    
    
    public void setConfig(MDLConfig newConfig)
    {
        config = newConfig;
        if (args != null) {
            for (Expression arg:args) {
                arg.setConfig(newConfig);
            }
        }
    }    
}

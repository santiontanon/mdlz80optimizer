/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.CodeStatement;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class ExpressionParser {
    MDLConfig config;
    
    public final String OP_STD_RSHIFT = ">>";
    public final String OP_STD_LSHIFT = "<<";
    public final String OP_STD_LOGICAL_NEGATION = "!";
    public final String OP_STD_LOGICAL_OR = "||";
    public final String OP_STD_LOGICAL_AND = "&&";
    public final String OP_STD_EQUAL = "=";
    public final String OP_STD_LOWERTHAN = "<";
    public final String OP_STD_GREATERTHAN = ">";
    public final String OP_STD_LEQTHAN = "<=";
    public final String OP_STD_GEQTHAN = ">=";
    public final String OP_STD_DIFF = "!=";
    public final String OP_STD_BIT_NEGATION = "~";
    public final String OP_STD_BIT_OR = "|";
    public final String OP_STD_BIT_AND = "&";
    public final String OP_STD_BIT_XOR = "^";
    public final String OP_STD_MOD = "%";
    
    public String OP_RSHIFT = ">>";
    public String OP_LSHIFT = "<<";
    public String OP_LOGICAL_NEGATION = "!";
    public String OP_LOGICAL_OR = "||";
    public String OP_LOGICAL_AND = "&&";
    public String OP_EQUAL = "=";
    public String OP_LOWERTHAN = "<";
    public String OP_GREATERTHAN = ">";
    public String OP_LEQTHAN = "<=";
    public String OP_GEQTHAN = ">=";
    public String OP_DIFF = "!=";
    public String OP_BIT_NEGATION = "~";
    public String OP_BIT_OR = "|";
    public String OP_BIT_AND = "&";
    public String OP_BIT_XOR = "^";
    public String OP_MOD = "%";
    
    public String default_parenthesis = "(";
    
    // make sure to add functions in lower case into this list:
    public List<String> dialectFunctions = new ArrayList<>();
    public HashMap<String, Integer> dialectFunctionsSingleArgumentNoParenthesisPrecedence = new HashMap<>();
    public List<String> dialectFunctionsOptionalSingleArgumentNoParenthesis = new ArrayList<>();

    public HashMap<String, String> opSynonyms = new HashMap<>();
    public HashMap<String, String> registerSynonyms = new HashMap<>();
    
    // dialect-specific variables:
    public List<Integer> sjasmCounterVariables = new ArrayList<>();
    public boolean sdccStyleHashMarksForConstants = false;
    public boolean sjasmPlusCurlyBracketExpressions = false;
    
    public boolean allowFloatingPointNumbers = false;
    public boolean binaryDigitsCanContainSpaces = false;

    // This is used by the macro80 dialect:
    public boolean doubleHashToMarkExternalSymbols = false;
    
    // indexed by the operator numbers:
    // Precedences obtained from the ones used by c++: https://en.cppreference.com/w/cpp/language/operator_precedence
    public int OPERATOR_PRECEDENCE[] = {
        -1, -1, -1, -1, 3, // af, 0, "a", symbol, - (unary) 
        -1, 6, 6, 5, 5,     // (, +, - (binary), *, /
        5, 13, 11, 10, 9,   // %, ||, &&, =, <
        9, 9, 9, 10, 16,    // >, <=, >=, !=, ?
        7, 7, 13, 11, 3,    // <<, >>, |, &, ~
        12, 3, -1, -1, -1}; // ^, !

    // This is a copy of the standard operator precedence, used to see if the precedences in the current
    // dialect differ from the usual:
    public final int STD_OPERATOR_PRECEDENCE[] = {
        -1, -1, -1, -1, 3,
        -1, 6, 6, 5, 5,     // (, +, -, *, /
        5, 13, 11, 10, 9,   // %, ||, &&, =, <
        9, 9, 9, 10, 16,    // >, <=, >=, !=, ?
        7, 7, 13, 11, 3,    // <<, >>, |, &, ~
        12, 3, -1, -1, -1}; // ^, !    

    public ExpressionParser(MDLConfig a_config)
    {
        config = a_config;
        
        opSynonyms.put("and", OP_LOGICAL_AND);
        opSynonyms.put("or", OP_LOGICAL_OR);
    }

    public void addOpSynonym(String synonym, String kw) {
        opSynonyms.put(synonym, kw);
    }


    public boolean isOp(String token, String kw) {
        if (token.equalsIgnoreCase(kw)) {
            return true;
        }
        token = token.toLowerCase();
        if (opSynonyms.containsKey(token)
                && opSynonyms.get(token).equalsIgnoreCase(kw)) {
            return true;
        }
        return false;
    }
    
    
    public void addRegisterSynonym(String synonym, String reg)
    {
        registerSynonyms.put(synonym, reg);
    }
    

    // "previous" is used for label scoping (it should be the statement that will be right before "s", after inserting "s"
    // into the SourceFile, since "s" might not have been yet inserted into it:
    public Expression parse(List<String> tokens, CodeStatement s, CodeStatement previous, CodeBase code)
    {
        Expression exp = parseInternal(tokens, s, previous, code);
        if (exp == null) return null;
        while(!tokens.isEmpty()) {
            if (tokens.get(0).equals("+")) {
                tokens.remove(0);
                if (exp.isRegister()) {
                    // special case for ix+nn (since I want the register to be separated from the expression)
                    Expression exp2 = parse(tokens, s, previous, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    return Expression.operatorExpression(Expression.EXPRESSION_SUM, exp, exp2, config);
                } else {
                    Expression exp2 = parseInternal(tokens, s, previous, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    exp = Expression.operatorExpression(Expression.EXPRESSION_SUM, exp, exp2, config);
                    continue;
                }
            }
            if (tokens.get(0).equals("-")) {
                if (exp.isRegister()) {
                    // special case for ix+nn (since I want the register to be separated from the expression)
                    // We do NOT remove the "-" from the token list, and just parse it as a sum
                    Expression exp2 = parse(tokens, s, previous, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    return Expression.operatorExpression(Expression.EXPRESSION_SUM, exp, exp2, config);
                } else {
                    tokens.remove(0);
                    Expression exp2 = parseInternal(tokens, s, previous, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator -");
                        return null;
                    }
                    exp = Expression.operatorExpression(Expression.EXPRESSION_SUB, exp, exp2, config);
                    continue;
                }
            }
            if (tokens.get(0).equals("*")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator *");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_MUL, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("/")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator /");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_DIV, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_MOD)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_MOD);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_MOD, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_BIT_OR)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_BIT_OR);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITOR, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_BIT_AND)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_BIT_AND);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITAND, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_BIT_XOR)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_BIT_XOR);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITXOR, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), OP_EQUAL, "==")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_EQUAL);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_EQUAL, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_LOWERTHAN)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_LOWERTHAN);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LOWERTHAN, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_GREATERTHAN)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_GREATERTHAN);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_GREATERTHAN, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_LEQTHAN)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_LEQTHAN);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LEQTHAN, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_GEQTHAN)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_GEQTHAN);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_GEQTHAN, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_DIFF)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_DIFF);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_DIFF, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("?")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (!tokens.isEmpty() && tokens.get(0).equals(":")) {
                    tokens.remove(0);
                    Expression exp3 = parse(tokens, s, previous, code);
                    exp = Expression.operatorTernaryExpression(Expression.EXPRESSION_TERNARY_IF, exp, exp2, exp3, config);
                    continue;
                } else {
                    config.error("Expected ':' in ternary if expression!");
                    return null;
                }
            }
            if (isOp(tokens.get(0), OP_LSHIFT)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_LSHIFT);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LSHIFT, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_RSHIFT)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_RSHIFT);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_RSHIFT, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_LOGICAL_OR)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_LOGICAL_OR);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_OR, exp, exp2, config);
                continue;
            }
            if (isOp(tokens.get(0), OP_LOGICAL_AND)) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator " + OP_LOGICAL_AND);
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_AND, exp, exp2, config);
                continue;
            }
            return exp;
        }

        return exp;
    }

    public Expression parseInternal(List<String> tokens, CodeStatement s, CodeStatement previous, CodeBase code)
    {
        boolean hashWasRemoved = false;
        if (sdccStyleHashMarksForConstants && tokens.size() >= 1 && tokens.get(0).equals("#")) {
            tokens.remove(0);
            hashWasRemoved = true;
        } 
        
        if (tokens.size() >= 1 &&
            config.tokenizer.isInteger(tokens.get(0))) {
            String token = tokens.remove(0);
            if (binaryDigitsCanContainSpaces) {
                if (config.tokenizer.isBinary(token)) {
                    String token2 = token;
                    int i = 0;
                    while(tokens.size()>i && config.tokenizer.isBinary(tokens.get(i))) {
                        token2 += tokens.get(i);
                        i++;
                    }
                    if (token2.toLowerCase().endsWith("b") ||
                        (tokens.size()>i && tokens.get(i).toLowerCase().endsWith("b"))) {
                        if (!token2.toLowerCase().endsWith("b")) {
                            token2 += tokens.get(i);
                            i++;
                        }
                        // binary token:
                        while(i>0) {
                            tokens.remove(0);
                            i--;
                        }
                        if (token2.length()<=9) {
                            return Expression.constantExpression(config.tokenizer.parseBinary(token2), Expression.RENDER_AS_8BITBIN, config);
                        } else {
                            return Expression.constantExpression(config.tokenizer.parseBinary(token2), Expression.RENDER_AS_16BITBIN, config);
                        }                        
                    }
                }
            }
            // integer constant:
            return Expression.constantExpression(Integer.parseInt(token), config);
        }
        if (allowFloatingPointNumbers && tokens.size() >= 1 &&
            config.tokenizer.isDouble(tokens.get(0))) {
            // double constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Double.parseDouble(token), config);
        }
        if (tokens.size() >= 1 &&
            config.tokenizer.isString(tokens.get(0))) {
            // string constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(config.tokenizer.stringValue(token), config);
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '9') &&
            (tokens.get(0).endsWith("h") || tokens.get(0).endsWith("H"))) {
            // should be a hex constant:
            String token = tokens.get(0);
            if (config.tokenizer.isHex(token)) {
                tokens.remove(0);
                if (token.length()<=3) {
                    // 8 bit:
                    return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
                } else {
                    // 16 bit:
                    return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0") || tokens.get(0).startsWith("1")) &&
            (tokens.get(0).endsWith("b") || tokens.get(0).endsWith("B"))) {
            // should be a binary constant:
            String token = tokens.get(0);
            if (config.tokenizer.isBinary(token)) {
                tokens.remove(0);
                if (token.length()<=9) {
                    return Expression.constantExpression(config.tokenizer.parseBinary(token), Expression.RENDER_AS_8BITBIN, config);
                } else {
                    return Expression.constantExpression(config.tokenizer.parseBinary(token), Expression.RENDER_AS_16BITBIN, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0b") || tokens.get(0).startsWith("0B"))) {
            // should be a binary constant:
            String token = tokens.get(0).substring(2);
            if (config.tokenizer.isBinary(token)) {
                tokens.remove(0);
                if (token.length()<=8) {
                    return Expression.constantExpression(config.tokenizer.parseBinary(token), Expression.RENDER_AS_8BITBIN, config);
                } else {
                    return Expression.constantExpression(config.tokenizer.parseBinary(token), Expression.RENDER_AS_16BITBIN, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '7') &&
            (tokens.get(0).endsWith("o") || tokens.get(0).endsWith("O") ||
             tokens.get(0).endsWith("q") || tokens.get(0).endsWith("Q"))) {
            // should be a octal constant:
            String token = tokens.get(0);
            if (config.tokenizer.isOctal(token)) {
                tokens.remove(0);
                return Expression.constantExpression(config.tokenizer.parseOctal(token), Expression.RENDER_AS_OCT, config);
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0q") || tokens.get(0).startsWith("0Q"))) {
            // should be a binary constant:
            String token = tokens.get(0).substring(2);
            if (config.tokenizer.isOctal(token)) {
                tokens.remove(0);
                return Expression.constantExpression(config.tokenizer.parseOctal(token), Expression.RENDER_AS_OCT, config);
            }
        }
        if (tokens.size() >= 1 && tokens.get(0).length() > 1 &&
            (tokens.get(0).startsWith("#") || tokens.get(0).startsWith("$")  || tokens.get(0).startsWith("&"))) {
            // should be a hex constant:
            String token = tokens.get(0);
            if (config.tokenizer.isHex(token)) {
                tokens.remove(0);
                if (token.length()<=3) {
                    // 8 bit:
                    return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
                } else {
                    // 16 bit:
                    return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0x") || tokens.get(0).startsWith("0X"))) {
            // should be a hex constant:
            String token = tokens.get(0).substring(2);
            if (config.tokenizer.isHex(token)) {
                tokens.remove(0);
                if (token.length()<=2) {
                    // 8 bit:
                    return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
                } else {
                    // 16 bit:
                    return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '9') &&
            (tokens.get(0).endsWith("d") || tokens.get(0).endsWith("D"))) {
            // should be a decimal constant:
            String token = tokens.get(0);
            token = token.substring(0, token.length()-1);
            if (config.tokenizer.isInteger(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Integer.parseInt(token), config);
            }
        }
        if (tokens.size()>=2 &&
            tokens.get(0).equalsIgnoreCase("x") &&
            config.tokenizer.isString(tokens.get(1)) &&
            config.tokenizer.isHex(config.tokenizer.stringValue(tokens.get(1)))) {
            tokens.remove(0);
            String token = config.tokenizer.stringValue(tokens.remove(0));
            if (token.length()<=2) {
                // 8 bit:
                return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
            } else {
                // 16 bit:
                return Expression.constantExpression(config.tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
            }            
        }
        
        if (tokens.size() >= 1) {
            // check if it's a dialect function:
            if (dialectFunctions.contains(tokens.get(0).toLowerCase())) {
                String token = tokens.remove(0);
                String functionName = token;
                List<Expression> args = new ArrayList<>();
                if (!tokens.isEmpty() && tokens.get(0).equals("(")) {
                    boolean first = true;
                    tokens.remove(0);
                    while(true) {
                        if (tokens.isEmpty()) {
                            config.error("Failed to parse argument list of a dialect function.");
                            return null;
                        }
                        if (tokens.get(0).equals(")")) {
                            tokens.remove(0);
                            break;
                        }
                        if (!first) {
                            if (tokens.isEmpty() || !tokens.get(0).equals(",")) {
                                config.error("Failed to parse argument list of a dialect function.");
                                return null;
                            }
                            tokens.remove(0);
                        }
                        Expression arg = parse(tokens, s, previous, code);
                        if (arg == null) {
                            config.error("Failed to parse argument list of a dialect function.");
                            return null;
                        }
                        args.add(arg);
                        first = false;
                    }
                }
                
                // Create it as is, then try to translate it:
                Expression exp = Expression.dialectFunctionExpression(functionName, args, config);
                Expression translated = config.dialectParser.translateToStandardExpression(functionName, args, s, code);
                if (translated != null) {
                    translated.originalDialectExpression = exp;
                    exp = translated;
                } else {
                    if (config.evaluateDialectFunctions) {
                        config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                    }
                }
                
                return exp;
                
            } else if (dialectFunctionsSingleArgumentNoParenthesisPrecedence.containsKey(tokens.get(0).toLowerCase())) {
                String functionName = tokens.remove(0);
                Expression arg = parse(tokens, s, previous, code);
                if (arg == null) {
                    config.error("Failed to parse argument list of a dialect function.");
                    return null;
                }
                                
                // Create it as is, then try to translate it:
                int precedence = dialectFunctionsSingleArgumentNoParenthesisPrecedence.get(functionName.toLowerCase());
                Expression exp = Expression.dialectFunctionExpressionMaybeTranslated(functionName, arg, precedence, s, code, config);
                return exp;
            } else if (dialectFunctionsOptionalSingleArgumentNoParenthesis.contains(tokens.get(0).toLowerCase())) {
                String token = tokens.remove(0);
                String functionName = token;
                List<Expression> args = new ArrayList<>();
                if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                    Expression arg = parse(tokens, s, previous, code);
                    if (arg == null) {
                        config.error("Failed to parse argument list of a dialect function.");
                        return null;
                    }
                    args.add(arg);
                }
                // Create it as is, then try to translate it:
                Expression exp = Expression.dialectFunctionExpression(functionName, args, config);
                Expression translated = config.dialectParser.translateToStandardExpression(functionName, args, s, code);
                if (translated != null) {
                    translated.originalDialectExpression = exp;
                    exp = translated;
                } else {
                    if (config.evaluateDialectFunctions) {
                        config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                    }
                }
                return exp;                
            } else if (config.tokenizer.isSymbol(tokens.get(0))) {
                String token = tokens.get(0);
                if (!config.caseSensitiveSymbols) token = token.toLowerCase();
                if (config.convertSymbolstoUpperCase) token = token.toUpperCase();

                if (previous == null && s != null && s.source != null) previous = s.source.getPreviousStatementTo(s, code);
                token = config.lineParser.newSymbolNameNotLabel(token, previous);
                if (token != null) {
                    boolean external = false;
                    tokens.remove(0);
                    
                    if (doubleHashToMarkExternalSymbols) {
                        if (tokens.size() >= 2 &&
                            tokens.get(0).equals("#") && tokens.get(1).equals("#")) {
                            tokens.remove(0);
                            tokens.remove(0);
                            external = true;
                        }
                    }
                    
                    Expression exp = Expression.symbolExpression(token, s, code, config);
                    exp.symbolIsExternal = external;
                    return exp;
                }
            }
        }
        // Check if it's a "%", "%%", "%%%", etc. sjasm counter variable:
        if (tokens.size() >= 1 && !sjasmCounterVariables.isEmpty()) {
            String token = tokens.get(0);
            boolean allPercent = true;
            for(int i = 0;i<token.length();i++) {
                if (token.charAt(i) != '%') {
                    allPercent = false;
                    break;
                }
            }
            if (allPercent) {
                // Make sure it's not a binary constant:
                int counterVariableIdx = token.length()-1;
                boolean canBeCounterVariable = true;
                if (sjasmCounterVariables.size() <= counterVariableIdx) {
                    canBeCounterVariable = false;
                }
                if (canBeCounterVariable && tokens.size() >= 2) {
                    if (config.tokenizer.isInteger(tokens.get(1))) {
                        canBeCounterVariable = false;
                    }
                }
                if (canBeCounterVariable) {
                    int value = sjasmCounterVariables.get(counterVariableIdx);
                    tokens.remove(0);
                    return Expression.constantExpression(value, config);
                }
            }
        }
        
        if (tokens.size() >= 2 &&
            (tokens.get(0).equals("%"))) {
            // should be a binary constant:
            String token = tokens.get(1);
            if (config.tokenizer.isBinary(token)) {
                tokens.remove(0);
                tokens.remove(0);
                if (binaryDigitsCanContainSpaces) {
                    while(!tokens.isEmpty() && config.tokenizer.isBinary(tokens.get(0))) {
                        token += tokens.remove(0);
                    }
                }
                if (token.length()<=9) {
                    return Expression.constantExpression(config.tokenizer.parseBinary(token), Expression.RENDER_AS_8BITBIN, config);
                } else {
                    return Expression.constantExpression(config.tokenizer.parseBinary(token), Expression.RENDER_AS_16BITBIN, config);
                }                
            }
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("-")) {
            // a negated expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, previous, code);
            if (exp != null) {
                if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                    exp.integerConstant = -exp.integerConstant;
                    return exp;
                } else {
                    return Expression.signChangeExpression(exp, config);
                }
            }
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("+")) {
            // might be something of the form: +1, or +(2-3)
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, previous, code);
            if (exp != null) {
                if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                    return exp;
                } else {
                    return Expression.operatorExpression(Expression.EXPRESSION_PLUS_SIGN, exp, config);
                }
            }
        }
        if (tokens.size() >= 2 &&
            StringUtils.equalsIgnoreCase(tokens.get(0), OP_BIT_NEGATION)) {
            // a bit negated expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, previous, code);
            return Expression.bitNegationExpression(exp, config);
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equalsIgnoreCase(OP_LOGICAL_NEGATION)) {
            // a logical negation expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, previous, code);
            return Expression.operatorExpression(Expression.EXPRESSION_LOGICAL_NEGATION, exp, config);
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("?")) {
            // variable name symbol:
            String token = tokens.remove(0);
            while(tokens.size() >= 2 && tokens.get(0).equals("?")) {
                token += tokens.remove(0);
            }
            token += tokens.remove(0);
            if (!config.caseSensitiveSymbols) token = token.toLowerCase();
            return Expression.symbolExpression(token, s, code, config);
        }
        if (tokens.size() >= 3 &&
            (tokens.get(0).equals("(") || tokens.get(0).equals("["))) {
            // a parenthesis expression:
            String parenthesis = tokens.remove(0);
            Expression exp = parse(tokens, s, previous, code);
            if (exp != null && tokens.size() >= 1 &&
                (tokens.get(0).equals(")") || tokens.get(0).equals("]"))) {
                tokens.remove(0);
                if (hashWasRemoved) {
                    // In SDCC syntax, when there is a # in front of a parenthesis,
                    // it marks a constant, and not an indirection, so, we remove the parenthesis:
                    return exp;
                } else {
                    return Expression.parenthesisExpression(exp, parenthesis, config);
                }
            }
        }
        if (tokens.size() >= 3 &&
            sjasmPlusCurlyBracketExpressions && tokens.get(0).equals("{")) {
            tokens.remove(0);
            List<Expression> args = new ArrayList<>();
            boolean readByte = false;
            if (tokens.get(0).equalsIgnoreCase("b")) {
                tokens.remove(0);
                readByte = true;
            }
            Expression exp = parse(tokens, s, previous, code);
            if (exp != null && tokens.size() >= 1 && tokens.get(0).equals("}")) {
                tokens.remove(0);
                args.add(exp);
//                Expression dexp = Expression.dialectFunctionExpression((readByte ? "{b":"{"), args, config);
                Object val = config.dialectParser.evaluateExpression((readByte ? "{b":"{"), args, s, code, true);
                if (val == null) return null;
                return Expression.constantExpression((Integer)val, config);
            }            
        }

        config.error("expression failed to parse with token list: " + tokens);
        return null;
    }
}

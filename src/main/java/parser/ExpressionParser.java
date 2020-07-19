/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceStatement;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class ExpressionParser {
    MDLConfig config;

    // make sure to add functions in lower case into this list:
    public List<String> dialectFunctions = new ArrayList<>();
    public List<String> dialectFunctionsSingleArgumentNoParenthesis = new ArrayList<>();


    public ExpressionParser(MDLConfig a_config)
    {
        config = a_config;
    }


    public Expression parse(List<String> tokens, SourceStatement s, CodeBase code)
    {
        Expression exp = parseInternal(tokens, s, code);
        if (exp == null) return null;
        while(!tokens.isEmpty()) {
            if (tokens.get(0).equals("+")) {
                tokens.remove(0);
                if (exp.isRegister(code)) {
                    // special case for ix+nn (since I want the register to be separated from the expression)
                    Expression exp2 = parse(tokens, s, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    return Expression.operatorExpression(Expression.EXPRESSION_SUM, exp, exp2, config);
                } else {
                    Expression exp2 = parseInternal(tokens, s, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    exp = Expression.operatorExpression(Expression.EXPRESSION_SUM, exp, exp2, config);
                    continue;
                }
            }
            if (tokens.get(0).equals("-")) {
                tokens.remove(0);
                if (exp.isRegister(code)) {
                    // special case for ix+nn (since I want the register to be separated from the expression)
                    Expression exp2 = parse(tokens, s, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    return Expression.operatorExpression(Expression.EXPRESSION_SUB, exp, exp2, config);
                } else {
                    Expression exp2 = parseInternal(tokens, s, code);
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
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator *");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_MUL, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("/")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator /");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_DIV, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "%", "MOD")) { // "MOD" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator %");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_MOD, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "|", "OR")) { // "OR" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator |");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITOR, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "&", "AND")) { // "AND" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator &");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITAND, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "^", "XOR")) { // "XOR" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator ^");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITXOR, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator =");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_EQUAL, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("<")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator <");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LOWERTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals(">")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator >");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_GREATERTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("<=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator <=");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LEQTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals(">=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator >=");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_GEQTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("!=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator !=");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_DIFF, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("?")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (tokens.get(0).equals(":")) {
                    tokens.remove(0);
                    Expression exp3 = parse(tokens, s, code);
                    exp = Expression.operatorTernaryExpression(Expression.EXPRESSION_TERNARY_IF, exp, exp2, exp3, config);
                    continue;
                } else {
                    config.error("Expected ':' in ternary if expression!");
                    return null;
                }
            }
            if (tokens.get(0).equals("<<")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator <<");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LSHIFT, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals(">>")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator >>");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_RSHIFT, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("||")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator ||");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_OR, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("&&")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator &&");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_AND, exp, exp2, config);
                continue;
            }
            return exp;
        }

        return exp;
    }

    public Expression parseInternal(List<String> tokens, SourceStatement s, CodeBase code)
    {
        if (tokens.size() >= 1 &&
            Tokenizer.isInteger(tokens.get(0))) {
            // integer constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Integer.parseInt(token), config);
        }
        if (tokens.size() >= 1 &&
            Tokenizer.isDouble(tokens.get(0))) {
            // double constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Double.parseDouble(token), config);
        }
        if (tokens.size() >= 1 &&
            Tokenizer.isString(tokens.get(0))) {
            // string constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Tokenizer.stringValue(token), config);
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '9') &&
            (tokens.get(0).endsWith("h") || tokens.get(0).endsWith("H"))) {
            // should be a hex constant:
            String token = tokens.get(0);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseHex(token), config);
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0") || tokens.get(0).startsWith("1")) &&
            (tokens.get(0).endsWith("b") || tokens.get(0).endsWith("B"))) {
            // should be a binary constant:
            String token = tokens.get(0);
            if (Tokenizer.isBinary(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseBinary(token), config);
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '7') &&
            (tokens.get(0).endsWith("o") || tokens.get(0).endsWith("O"))) {
            // should be a binary constant:
            String token = tokens.get(0);
            if (Tokenizer.isOctal(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseOctal(token), config);
            }
        }
        if (tokens.size() >= 1 && tokens.get(0).length() > 1 &&
            (tokens.get(0).startsWith("#") || tokens.get(0).startsWith("$"))) {
            // should be a hex constant:
            String token = tokens.get(0);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseHex(token), config);
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0x") || tokens.get(0).startsWith("0x"))) {
            // should be a hex constant:
            String token = tokens.get(0).substring(2);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseHex(token), config);
            }
        }
        if (tokens.size() >= 1 &&
            Tokenizer.isSymbol(tokens.get(0))) {
            // symbol:
            String token = tokens.remove(0);

            if (dialectFunctions.contains(token.toLowerCase())) {
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
                        Expression arg = parse(tokens, s, code);
                        if (arg == null) {
                            config.error("Failed to parse argument list of a dialect function.");
                            return null;
                        }
                        args.add(arg);
                        first = false;
                    }
                }
                Expression exp = Expression.dialectFunctionExpression(functionName, args, config);
                if (exp != null) {
                    if (config.evaluateDialectFunctions) {
                        config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                    }
                }
                return exp;
                
            } else if (dialectFunctionsSingleArgumentNoParenthesis.contains(token.toLowerCase())) {
                String functionName = token;
                List<Expression> args = new ArrayList<>();
                Expression arg = parse(tokens, s, code);
                if (arg == null) {
                    config.error("Failed to parse argument list of a dialect function.");
                    return null;
                }
                args.add(arg);
                Expression exp = Expression.dialectFunctionExpression(functionName, args, config);
                if (exp != null) {
                    if (config.evaluateDialectFunctions) {
                        config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                    }
                }
                return exp;
            } else {
                if (config.dialectParser != null) token = config.dialectParser.symbolName(token);
                return Expression.symbolExpression(token, code, config);
            }
        }
        if (tokens.size() >= 2 &&
            (tokens.get(0).equals("%"))) {
            // should be a binary constant:
            String token = tokens.get(1);
            if (Tokenizer.isBinary(token)) {
                tokens.remove(0);
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseBinary(token), config);
            }
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("-")) {
            // a negated expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, code);
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
            // Check if it's just a number with a plus in the front:
            if (Tokenizer.isInteger(tokens.get(1))) {
                tokens.remove(0);
                Expression exp = parseInternal(tokens, s, code);
                if (exp != null) {
                    if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                        return exp;
                    } else {
                        // something weird happened:
                        config.error("expression failed to parse with token list: " + tokens);
                        return null;
                    }
                } else {
                    // something weird happened:
                    config.error("expression failed to parse with token list: " + tokens);
                    return null;
                }
            }
        }
        if (tokens.size() >= 2 &&
            StringUtils.equalsAnyIgnoreCase(tokens.get(0), "~", "NOT")) { // "NOT" is tniASM syntax
            // a bit negated expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, code);
            return Expression.bitNegationExpression(exp, config);
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("!")) {
            // a logical negation expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, code);
            return Expression.operatorExpression(Expression.EXPRESSION_LOGICAL_NEGATION, exp, config);
        }

        if (tokens.size() >= 2 &&
            tokens.get(0).equals("?")) {
            tokens.remove(0);
            // variable name symbol:
            String token = "?" + tokens.remove(0);
            return Expression.symbolExpression(token, code, config);
        }
        if (tokens.size() >= 3 &&
            (tokens.get(0).equals("(") || tokens.get(0).equals("["))) {
            // a parenthesis expression:
            tokens.remove(0);
            Expression exp = parse(tokens, s, code);
            if (exp != null && tokens.size() >= 1 &&
                (tokens.get(0).equals(")") || tokens.get(0).equals("]"))) {
                tokens.remove(0);
                return Expression.parenthesisExpression(exp, config);
            }
        }

        config.error("expression failed to parse with token list: " + tokens);
        return null;
    }
}

/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.Expression;
import code.CodeBase;
import java.util.List;

public class ExpressionParser {
    MDLConfig config;

    public ExpressionParser(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    public Expression parse(List<String> tokens, CodeBase code)
    {
        Expression exp = parseInternal(tokens, code);
        if (exp == null) return null;
        if (tokens.isEmpty()) return exp;
        
        if (tokens.get(0).equals("+")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator +");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_SUM, exp, exp2, config);
        }
        if (tokens.get(0).equals("-")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator -");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_SUB, exp, exp2, config);
        }
        if (tokens.get(0).equals("*")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator *");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_MUL, exp, exp2, config);
        }
        if (tokens.get(0).equals("/")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator /");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_DIV, exp, exp2, config);
        }
        if (tokens.get(0).equals("%")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator %");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_MOD, exp, exp2, config);
        }
        if (tokens.get(0).equals("|")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator |");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_OR, exp, exp2, config);
        }
        if (tokens.get(0).equals("&")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_AND, exp, exp2, config);
        }
        if (tokens.get(0).equals("=")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_EQUAL, exp, exp2, config);
        }
        if (tokens.get(0).equals("<")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_LOWERTHAN, exp, exp2, config);
        }
        if (tokens.get(0).equals(">")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_GREATERTHAN, exp, exp2, config);
        }
        if (tokens.get(0).equals("<=")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_LEQTHAN, exp, exp2, config);
        }
        if (tokens.get(0).equals(">=")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_GEQTHAN, exp, exp2, config);
        }
        if (tokens.get(0).equals("!=")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (exp2 == null) {
                config.error("Missing argument for operator &");
                return null;
            }
            return Expression.operatorExpression(Expression.EXPRESSION_DIFF, exp, exp2, config);
        }
        if (tokens.get(0).equals("?")) {
            tokens.remove(0);
            Expression exp2 = parse(tokens, code);
            if (tokens.get(0).equals(":")) {
                tokens.remove(0);
                Expression exp3 = parse(tokens, code);
                return Expression.operatorTernaryExpression(Expression.EXPRESSION_TERNARY_IF, exp, exp2, exp3, config);
            } else {
                config.error("Expected ':' in ternary if expression!");
                return null;                
            }
        }
        
        return exp;
    }
    
    public Expression parseInternal(List<String> tokens, CodeBase code)
    {     
        if (tokens.size() >= 1 &&
            Tokenizer.isInteger(tokens.get(0))) {
            // decimal constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Integer.parseInt(token));
        }
        if (tokens.size() >= 1 &&
            Tokenizer.isString(tokens.get(0))) {
            // string constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Tokenizer.stringValue(token));
        }
        if (tokens.size() >= 1 && 
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '9') &&
            (tokens.get(0).endsWith("h") || tokens.get(0).endsWith("H"))) {
            // should be a hex constant:
            String token = tokens.get(0);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseHex(token));
            }
        }
        if (tokens.size() >= 1 && 
            (tokens.get(0).startsWith("0") || tokens.get(0).startsWith("1")) && 
            (tokens.get(0).endsWith("b") || tokens.get(0).endsWith("B"))) {
            // should be a binary constant:
            String token = tokens.get(0);
            if (Tokenizer.isBinary(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseBinary(token));
            }
        }
        if (tokens.size() >= 2 && 
            (tokens.get(0).equals("#") || tokens.get(0).equals("$"))) {
            // should be a hex constant:
            String token = tokens.get(1);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseHex(token));
            }
        }
        if (tokens.size() >= 1 && 
            (tokens.get(0).startsWith("0x") || tokens.get(0).startsWith("0x"))) {
            // should be a hex constant:
            String token = tokens.get(0).substring(2);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseHex(token));
            }
        }
        if (tokens.size() >= 1 &&
            Tokenizer.isSymbol(tokens.get(0))) {
            // symbol:
            String token = tokens.remove(0);
            return Expression.symbolExpression(token, code);
        }        
        if (tokens.size() >= 2 && 
            (tokens.get(0).equals("%"))) {
            // should be a binary constant:
            String token = tokens.get(1);
            if (Tokenizer.isBinary(token)) {
                tokens.remove(0);
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseBinary(token));
            }
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("-")) {
            // a negated expression:
            tokens.remove(0);
            Expression exp = parse(tokens, code);
            if (exp != null) {
                if (exp.type == Expression.EXPRESSION_NUMERIC_CONSTANT) {
                    exp.numericConstant = -exp.numericConstant;
                    return exp;
                } else {
                    return Expression.negationExpression(exp);
                }
            }
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("?")) {
            tokens.remove(0);
            // variable name symbol:
            String token = "?" + tokens.remove(0);
            return Expression.symbolExpression(token, code);
        }
        if (tokens.size() >= 3 &&
            tokens.get(0).equals("(")) {
            // a parenthesis expression:
            tokens.remove(0);
            Expression exp = parse(tokens, code);
            if (exp != null && tokens.size() >= 1 &&
                tokens.get(0).equals(")")) {
                tokens.remove(0);
                return Expression.parenthesisExpression(exp);
            }
        }
        
        config.error("expression failed to parse with token list: " + tokens);
        return null;
    }
}

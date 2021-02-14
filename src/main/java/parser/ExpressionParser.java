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

    // dialect-specific variables:
    public List<Integer> sjasmConterVariables = new ArrayList<>();
    public boolean sdccStyleHashMarksForConstants = false;
    public boolean sjasmPlusCurlyBracketExpressions = false;
    
    public boolean allowFloatingPointNumbers = false;
    public boolean caseSensitiveSymbols = true;
    

    public ExpressionParser(MDLConfig a_config)
    {
        config = a_config;
    }


    // "previous" is used for label scoping (it should be the statement that will be right before "s", after inserting "s"
    // into the SourceFile, since "s" might not have been yet inserted into it:
    public Expression parse(List<String> tokens, SourceStatement s, SourceStatement previous, CodeBase code)
    {
        Expression exp = parseInternal(tokens, s, previous, code);
        if (exp == null) return null;
        while(!tokens.isEmpty()) {
            if (tokens.get(0).equals("+")) {
                tokens.remove(0);
                if (exp.isRegister(code)) {
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
                tokens.remove(0);
                if (exp.isRegister(code)) {
                    // special case for ix+nn (since I want the register to be separated from the expression)
                    Expression exp2 = parse(tokens, s, previous, code);
                    if (exp2 == null) {
                        config.error("Missing argument for operator +");
                        return null;
                    }
                    return Expression.operatorExpression(Expression.EXPRESSION_SUB, exp, exp2, config);
                } else {
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
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "%", "MOD")) { // "MOD" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator %");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_MOD, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "|", "OR")) { // "OR" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator |");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITOR, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "&", "AND")) { // "AND" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator &");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITAND, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "^", "XOR")) { // "XOR" is tniASM syntax
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator ^");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_BITXOR, exp, exp2, config);
                continue;
            }
            if (StringUtils.equalsAnyIgnoreCase(tokens.get(0), "=", "==")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator =");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_EQUAL, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("<")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator <");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LOWERTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals(">")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator >");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_GREATERTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("<=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator <=");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LEQTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals(">=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator >=");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_GEQTHAN, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("!=")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator !=");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_DIFF, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("?")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (tokens.get(0).equals(":")) {
                    tokens.remove(0);
                    Expression exp3 = parse(tokens, s, previous, code);
                    exp = Expression.operatorTernaryExpression(Expression.EXPRESSION_TERNARY_IF, exp, exp2, exp3, config);
                    continue;
                } else {
                    config.error("Expected ':' in ternary if expression!");
                    return null;
                }
            }
            if (tokens.get(0).equals("<<")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator <<");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_LSHIFT, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals(">>")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator >>");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_RSHIFT, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("||")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
                if (exp2 == null) {
                    config.error("Missing argument for operator ||");
                    return null;
                }
                exp = Expression.operatorExpression(Expression.EXPRESSION_OR, exp, exp2, config);
                continue;
            }
            if (tokens.get(0).equals("&&")) {
                tokens.remove(0);
                Expression exp2 = parseInternal(tokens, s, previous, code);
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

    public Expression parseInternal(List<String> tokens, SourceStatement s, SourceStatement previous, CodeBase code)
    {
        boolean hashWasRemoved = false;
        if (sdccStyleHashMarksForConstants && tokens.size() >= 1 && tokens.get(0).equals("#")) {
            tokens.remove(0);
            hashWasRemoved = true;
        } 
        
        if (tokens.size() >= 1 &&
            Tokenizer.isInteger(tokens.get(0))) {
            // integer constant:
            String token = tokens.remove(0);
            return Expression.constantExpression(Integer.parseInt(token), config);
        }
        if (allowFloatingPointNumbers && tokens.size() >= 1 &&
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
                if (token.length()<=3) {
                    // 8 bit:
                    return Expression.constantExpression(Tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
                } else {
                    // 16 bit:
                    return Expression.constantExpression(Tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0") || tokens.get(0).startsWith("1")) &&
            (tokens.get(0).endsWith("b") || tokens.get(0).endsWith("B"))) {
            // should be a binary constant:
            String token = tokens.get(0);
            if (Tokenizer.isBinary(token)) {
                tokens.remove(0);
                if (token.length()<=9) {
                    return Expression.constantExpression(Tokenizer.parseBinary(token), Expression.RENDER_AS_8BITBIN, config);
                } else {
                    return Expression.constantExpression(Tokenizer.parseBinary(token), Expression.RENDER_AS_16BITBIN, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0b") || tokens.get(0).startsWith("0B"))) {
            // should be a binary constant:
            String token = tokens.get(0).substring(2);
            if (Tokenizer.isBinary(token)) {
                tokens.remove(0);
                if (token.length()<=8) {
                    return Expression.constantExpression(Tokenizer.parseBinary(token), Expression.RENDER_AS_8BITBIN, config);
                } else {
                    return Expression.constantExpression(Tokenizer.parseBinary(token), Expression.RENDER_AS_16BITBIN, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).charAt(0) >= '0' && tokens.get(0).charAt(0) <= '7') &&
            (tokens.get(0).endsWith("o") || tokens.get(0).endsWith("O"))) {
            // should be a octal constant:
            String token = tokens.get(0);
            if (Tokenizer.isOctal(token)) {
                tokens.remove(0);
                return Expression.constantExpression(Tokenizer.parseOctal(token), Expression.RENDER_AS_OCT, config);
            }
        }
        if (tokens.size() >= 1 && tokens.get(0).length() > 1 &&
            (tokens.get(0).startsWith("#") || tokens.get(0).startsWith("$")  || tokens.get(0).startsWith("&"))) {
            // should be a hex constant:
            String token = tokens.get(0);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                if (token.length()<=3) {
                    // 8 bit:
                    return Expression.constantExpression(Tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
                } else {
                    // 16 bit:
                    return Expression.constantExpression(Tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
                }
            }
        }
        if (tokens.size() >= 1 &&
            (tokens.get(0).startsWith("0x") || tokens.get(0).startsWith("0X"))) {
            // should be a hex constant:
            String token = tokens.get(0).substring(2);
            if (Tokenizer.isHex(token)) {
                tokens.remove(0);
                if (token.length()<=2) {
                    // 8 bit:
                    return Expression.constantExpression(Tokenizer.parseHex(token), Expression.RENDER_AS_8BITHEX, config);
                } else {
                    // 16 bit:
                    return Expression.constantExpression(Tokenizer.parseHex(token), Expression.RENDER_AS_16BITHEX, config);
                }
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
                // First, try to translate it:
                Expression exp = config.dialectParser.translateToStandardExpression(functionName, args, s, code);
                if (exp == null) {
                    // otherwise, we just create it as is:
                    exp = Expression.dialectFunctionExpression(functionName, args, config);
                    if (exp != null) {
                        if (config.evaluateDialectFunctions) {
                            config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                        }
                    }
                }
                return exp;
                
            } else if (dialectFunctionsSingleArgumentNoParenthesis.contains(tokens.get(0).toLowerCase())) {
                String token = tokens.remove(0);
                String functionName = token;
                List<Expression> args = new ArrayList<>();
                Expression arg = parse(tokens, s, previous, code);
                if (arg == null) {
                    config.error("Failed to parse argument list of a dialect function.");
                    return null;
                }
                args.add(arg);
                // First, try to translate it:
                Expression exp = config.dialectParser.translateToStandardExpression(functionName, args, s, code);
                if (exp == null) {
                    // otherwise, we just create it as is:
                    exp = Expression.dialectFunctionExpression(functionName, args, config);
                    if (exp != null) {
                        if (config.evaluateDialectFunctions) {
                            config.codeBaseParser.expressionsToReplaceByValueAtTheEnd.add(Pair.of(exp, s));
                        }
                    }
                }
                return exp;
            } else if (Tokenizer.isSymbol(tokens.get(0))) {
                String token = tokens.remove(0);
                if (!caseSensitiveSymbols) token = token.toLowerCase();

                if (previous == null && s != null && s.source != null) previous = s.source.getPreviousStatementTo(s, code);
                token = config.lineParser.newSymbolNameNotLabel(token, previous);
                return Expression.symbolExpression(token, s, code, config);
            }
        }
        // Check if it's a "%", "%%", "%%%", etc. sjasm counter variable:
        if (tokens.size() >= 1 && !sjasmConterVariables.isEmpty()) {
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
                if (sjasmConterVariables.size() <= counterVariableIdx) {
                    canBeCounterVariable = false;
                }
                if (canBeCounterVariable && tokens.size() >= 2) {
                    if (Tokenizer.isInteger(tokens.get(1))) {
                        canBeCounterVariable = false;
                    }
                }
                if (canBeCounterVariable) {
                    int value = sjasmConterVariables.get(counterVariableIdx);
                    tokens.remove(0);
                    return Expression.constantExpression(value, config);
                }
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
            StringUtils.equalsAnyIgnoreCase(tokens.get(0), "~", "NOT")) { // "NOT" is tniASM syntax
            // a bit negated expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, previous, code);
            return Expression.bitNegationExpression(exp, config);
        }
        if (tokens.size() >= 2 &&
            tokens.get(0).equals("!")) {
            // a logical negation expression:
            tokens.remove(0);
            Expression exp = parseInternal(tokens, s, previous, code);
            return Expression.operatorExpression(Expression.EXPRESSION_LOGICAL_NEGATION, exp, config);
        }

        if (tokens.size() >= 2 &&
            tokens.get(0).equals("?")) {
            tokens.remove(0);
            // variable name symbol:
            String token = "?" + tokens.remove(0);
            if (!caseSensitiveSymbols) token = token.toLowerCase();
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
                return Expression.dialectFunctionExpression((readByte ? "{b":"{"), args, config);
            }            
        }

        config.error("expression failed to parse with token list: " + tokens);
        return null;
    }
}

/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.util.List;

import org.junit.Test;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import parser.ExpressionParser;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class ExpressionTest {

    @Test
    public void test()
    {
        int failures = 0;
        failures += expressionTest("2", 2);
        failures += expressionTest("af", null);
        failures += expressionTest("2+2", 4);
        failures += expressionTest("2+2*5", 12);
        failures += expressionTest("10 - 5 - 2", 3);
        failures += expressionTest("10 - 5 * 2", 0);
        failures += expressionTest("10 * 5 - 2", 48);
        failures += expressionTest("10 - 5 * -2 - 4", 16);
        failures += expressionTest("-10 << 1", -20);
        failures += expressionTest("-(10 << 1)", -20);
        failures += expressionTest("#38 & #1c", 0x18);
        failures += expressionTest("#38 && #1c", Expression.TRUE);
        failures += expressionTest("3 <= 4", Expression.TRUE);
        failures += expressionTest("7 & ~3", 0x4);
        if (failures > 0) {
            throw new Error(failures + " tests failed!");
        }
    }


    public static int expressionTest(String line, Integer expected)
    {
        MDLConfig config = new MDLConfig();
        List<String> tokens = Tokenizer.tokenize(line);
        ExpressionParser p = new ExpressionParser(config);
        CodeBase code = new CodeBase(config);
        Expression exp = p.parse(tokens, code);
        System.out.println(exp);
        if (expected == null) {
            if (exp.evaluatesToNumericConstant()) {
                System.err.println("Expected " + expected + ", but expression evaluates to numeric constant");
                return 1;
            } else {
                return 0;
            }
        } else {
            if (exp.evaluatesToNumericConstant()) {
                Integer actual = exp.evaluate(null, code, false);
                if (actual.equals(expected)) {
                    return 0;
                } else {
                    System.err.println("Expected " + expected + ", but expression evaluates to " + actual);
                    return 1;
                }
            } else {
                System.err.println("Expected " + expected + ", but expression does not evaluate to numeric constant.");
                return 1;
            }
        }
    }
}

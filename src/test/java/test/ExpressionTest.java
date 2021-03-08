/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import parser.CodeBaseParser;
import parser.ExpressionParser;
import parser.LineParser;

/**
 *
 * @author santi
 */
public class ExpressionTest {

    private final MDLConfig config;
    private final CodeBase code;

    public ExpressionTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
        config.codeBaseParser = new CodeBaseParser(config);
        config.lineParser = new LineParser(config, config.codeBaseParser);      
        config.expressionParser = new ExpressionParser(config);
        config.expressionParser.allowFloatingPointNumbers = true;
    }

    @Test public void test1() { Assert.assertEquals(Integer.valueOf(2), evaluate("2")); }
    @Test public void test2() { Assert.assertEquals(null, evaluate("af")); }
    @Test public void test3() { Assert.assertEquals(Integer.valueOf(4), evaluate("2+2")); }
    @Test public void test4() { Assert.assertEquals(Integer.valueOf(12), evaluate("2+2*5")); }
    @Test public void test5() { Assert.assertEquals(Integer.valueOf(3), evaluate("10 - 5 - 2")); }
    @Test public void test6() { Assert.assertEquals(Integer.valueOf(0), evaluate("10 - 5 * 2")); }
    @Test public void test7() { Assert.assertEquals(Integer.valueOf(48), evaluate("10 * 5 - 2")); }
    @Test public void test8() { Assert.assertEquals(Integer.valueOf(16), evaluate("10 - 5 * -2 - 4")); }
    @Test public void test9() { Assert.assertEquals(Integer.valueOf(-20), evaluate("-10 << 1")); }
    @Test public void test10() { Assert.assertEquals(Integer.valueOf(-20), evaluate("-(10 << 1)")); }
    @Test public void test11() { Assert.assertEquals(Integer.valueOf(0x18), evaluate("#38 & #1c")); }
    @Test public void test12() { Assert.assertEquals(Integer.valueOf(Expression.TRUE), evaluate("#38 && #1c")); }
    @Test public void test13() { Assert.assertEquals(Integer.valueOf(Expression.TRUE), evaluate("3 <= 4")); }
    @Test public void test14() { Assert.assertEquals(Integer.valueOf(0x4), evaluate("7 & ~3")); }
    @Test public void test15() { Assert.assertEquals(Integer.valueOf(2), evaluate("#01+$01")); }
    @Test public void test16() { Assert.assertEquals(Integer.valueOf(Expression.FALSE), evaluate("!(3 <= 4)")); }
    @Test public void test17() { Assert.assertEquals(Double.valueOf(0.05), evaluate("0.05")); }
    @Test public void test18() { Assert.assertEquals(Double.valueOf(0.10), evaluate("0.05 * 2")); }
    @Test public void test19() { Assert.assertEquals(Integer.valueOf(Expression.TRUE), evaluate("0.05 < 1")); }
    @Test public void test20() { Assert.assertEquals(Integer.valueOf(0x99), evaluate("0x99")); }
    @Test public void test21() { Assert.assertEquals(Integer.valueOf(0x99), evaluate("0X99")); }
    @Test public void test22() { 
        config.tokenizer.allowAndpersandHex = true;
        Assert.assertEquals(Integer.valueOf(0xc0de), evaluate("&C0DE")); 
        config.tokenizer.allowAndpersandHex = false;
    }
    @Test public void test23() { Assert.assertEquals(Integer.valueOf(1), evaluate("+(1)")); }
    @Test public void test24() { Assert.assertEquals(Integer.valueOf(1), evaluate("+1")); }
    @Test public void test25() { Assert.assertEquals(Integer.valueOf(144), evaluate("32*4+4*4")); }
    @Test public void test26() { Assert.assertEquals(Integer.valueOf(Expression.FALSE), evaluate("144!=32*4+4*4")); }
    @Test public void test27() { Assert.assertEquals(Integer.valueOf(97), evaluate("100 - 5 + 2")); }
    @Test public void test28() { Assert.assertEquals(Integer.valueOf(1), evaluate("#00 & ~1 | 1")); }
    @Test public void test29() { Assert.assertEquals(Integer.valueOf('a'*256+'f'), evaluate("\"af\"")); }

    private Object evaluate(String line)
    {
        List<String> tokens = config.tokenizer.tokenize(line);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        System.out.println(exp);

        if (exp.evaluatesToIntegerConstant()) {
            return exp.evaluateToInteger(null, code, false);
        } else if (exp.evaluatesToNumericConstant()) {
            return exp.evaluate(null, code, false);
        } else {
            return null;
        }
    }
}

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
import parser.ExpressionParser;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class ExpressionTest {

    private final CodeBase codeBase;
    private final ExpressionParser expressionParser;

    public ExpressionTest() {
        MDLConfig mdlConfig = new MDLConfig();
        codeBase = new CodeBase(mdlConfig);
        expressionParser = new ExpressionParser(mdlConfig);
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

    private Integer evaluate(String line)
    {
        List<String> tokens = Tokenizer.tokenize(line);
        Expression exp = expressionParser.parse(tokens, null, codeBase);
        System.out.println(exp);

        return exp.evaluatesToIntegerConstant()
                ? exp.evaluate(null, codeBase, false)
                : null;
    }
}

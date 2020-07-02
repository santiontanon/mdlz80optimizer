/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import cl.MDLConfig;
import code.CodeBase;
import workers.SourceCodeGenerator;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class PatternBasedOptimizerTest {

    private final MDLConfig mdlConfig;
    private final CodeBase codeBase;

    public PatternBasedOptimizerTest() {
        mdlConfig = new MDLConfig();
        codeBase = new CodeBase(mdlConfig);
    }

    @Test public void test1() throws IOException { Assert.assertEquals(3, test("data/tests/test1.asm")); }
    @Test public void test2() throws IOException { Assert.assertEquals(1, test("data/tests/test2.asm")); }
    @Test public void test3() throws IOException { Assert.assertEquals(0, test("data/tests/test3.asm")); }
    @Test public void test4() throws IOException { Assert.assertEquals(0, test("data/tests/test4.asm")); }
    @Test public void test5() throws IOException { Assert.assertEquals(0, test("data/tests/test5.asm")); }
    @Test public void test6() throws IOException { Assert.assertEquals(2, test("data/tests/test6.asm")); }
    @Test public void test7() throws IOException { Assert.assertEquals(2, test("data/tests/test7.asm")); }
    @Test public void test8() throws IOException { Assert.assertEquals(2, test("data/tests/test8.asm")); }
    @Test public void test9() throws IOException { Assert.assertEquals(0, test("data/tests/test9.asm")); }
    @Test public void test10() throws IOException { Assert.assertEquals(2, test("data/tests/test10.asm")); }
    @Test public void test11() throws IOException { Assert.assertEquals(3, test("data/tests/test11.asm")); }
    @Test public void test12() throws IOException { Assert.assertEquals(2, test("data/tests/test12.asm")); }
    @Test public void test13() throws IOException { Assert.assertEquals(0, test("data/tests/test13.asm")); }
    @Test public void test14() throws IOException { Assert.assertEquals(1, test("data/tests/test14.asm")); }
    @Test public void test15() throws IOException { Assert.assertEquals(2, test("data/tests/test15.asm")); }
    @Test public void test16() throws IOException { Assert.assertEquals(5, test("data/tests/test16.asm")); }
    @Test public void test17() throws IOException { Assert.assertEquals(4, test("data/tests/test17.asm")); }
    @Test public void test18() throws IOException { Assert.assertEquals(3, test("data/tests/test18.asm")); }
    @Test public void test19() throws IOException { Assert.assertEquals(2, test("data/tests/test19.asm")); }
    @Test public void test20() throws IOException { Assert.assertEquals(4, test("data/tests/test20.asm")); }
    @Test public void test21() throws IOException { Assert.assertEquals(3, test("data/tests/test21.asm")); }
    @Test public void test22() throws IOException { Assert.assertEquals(2, test("data/tests/test22.asm")); }
    @Test public void test23() throws IOException { Assert.assertEquals(1, test("data/tests/test23.asm")); }
    @Test public void test24() throws IOException { Assert.assertEquals(0, test("data/tests/test24.asm")); }

    private int test(String inputFile) throws IOException
    {
        Assert.assertTrue(mdlConfig.parse(inputFile));
        Assert.assertNotNull(
                "Could not parse file " + inputFile,
                mdlConfig.codeBaseParser.parseMainSourceFile(mdlConfig.inputFile, codeBase));

        PatternBasedOptimizer pbo = new PatternBasedOptimizer(mdlConfig);
        PatternBasedOptimizer.OptimizationResult r = pbo.optimize(codeBase);

        SourceCodeGenerator scg = new SourceCodeGenerator(mdlConfig);

        String result = scg.sourceFileString(codeBase.getMain());
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");

        return r.bytesSaved;
    }
}

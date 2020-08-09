/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;
import workers.AnnotatedSourceCodeGenerator;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class PatternBasedOptimizerTest {

    private final MDLConfig config;
    private final CodeBase code;
    private final PatternBasedOptimizer pbo;

    public PatternBasedOptimizerTest() {
        config = new MDLConfig();
        pbo = new PatternBasedOptimizer(config);
        config.registerWorker(pbo);
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertEquals(4, test("data/tests/test1.asm")); }
    @Test public void test2() throws IOException { Assert.assertEquals(4, test("data/tests/test2.asm")); }
    @Test public void test3() throws IOException { Assert.assertEquals(1, test("data/tests/test3.asm")); }
    @Test public void test4() throws IOException { Assert.assertEquals(1, test("data/tests/test4.asm")); }
    @Test public void test5() throws IOException { Assert.assertEquals(1, test("data/tests/test5.asm")); }
    @Test public void test6() throws IOException { Assert.assertEquals(4, test("data/tests/test6.asm")); }
    @Test public void test7() throws IOException { Assert.assertEquals(2, test("data/tests/test7.asm")); }
    @Test public void test8() throws IOException { Assert.assertEquals(3, test("data/tests/test8.asm")); }
    @Test public void test9() throws IOException { Assert.assertEquals(0, test("data/tests/test9.asm")); }
    @Test public void test10() throws IOException { Assert.assertEquals(2, test("data/tests/test10.asm")); }
    @Test public void test11() throws IOException { Assert.assertEquals(4, test("data/tests/test11.asm")); }
    @Test public void test12() throws IOException { Assert.assertEquals(3, test("data/tests/test12.asm")); }
    @Test public void test13() throws IOException { Assert.assertEquals(1, test("data/tests/test13.asm")); }
    @Test public void test14() throws IOException { Assert.assertEquals(2, test("data/tests/test14.asm")); }
    @Test public void test15() throws IOException { Assert.assertEquals(3, test("data/tests/test15.asm")); }
    @Test public void test16() throws IOException { Assert.assertEquals(6, test("data/tests/test16.asm")); }
    @Test public void test17() throws IOException { Assert.assertEquals(6, test("data/tests/test17.asm")); }
    @Test public void test18() throws IOException { Assert.assertEquals(6, test("data/tests/test18.asm")); }
    @Test public void test19() throws IOException { Assert.assertEquals(3, test("data/tests/test19.asm")); }
    @Test public void test20() throws IOException { Assert.assertEquals(9, test("data/tests/test20.asm")); }
    @Test public void test21() throws IOException { Assert.assertEquals(4, test("data/tests/test21.asm")); }
    @Test public void test22() throws IOException { Assert.assertEquals(3, test("data/tests/test22.asm")); }
    @Test public void test23() throws IOException { Assert.assertEquals(2, test("data/tests/test23.asm")); }
    @Test public void test24() throws IOException { Assert.assertEquals(1, test("data/tests/test24.asm")); }
    @Test public void test25() throws IOException { Assert.assertEquals(1, test("data/tests/test25.asm")); }
    @Test public void test26() throws IOException { Assert.assertEquals(1, test("data/tests/test26.asm")); }
    @Test public void test27() throws IOException { Assert.assertEquals(5, test("data/tests/test27.asm")); }
    @Test public void test28() throws IOException { Assert.assertEquals(7, test("data/tests/test28.asm")); }
    @Test public void test29() throws IOException { Assert.assertEquals(3, test("data/tests/test29.asm")); }
    @Test public void test30() throws IOException { Assert.assertEquals(2, test("data/tests/test30.asm")); }
    @Test public void test31() throws IOException { Assert.assertEquals(8, test("data/tests/test31.asm")); }
    @Test public void test32() throws IOException { Assert.assertEquals(4, test("data/tests/test32.asm")); }
    @Test public void test33() throws IOException { Assert.assertEquals(3, test("data/tests/test33.asm")); }
    @Test public void test34() throws IOException { Assert.assertEquals(1, test("data/tests/test34.asm")); }
    @Test public void test35() throws IOException { Assert.assertEquals(1, test("data/tests/test35.asm")); }
    @Test public void test36() throws IOException { Assert.assertEquals(4, test("data/tests/test36.asm")); }
    @Test public void test37() throws IOException { Assert.assertEquals(3, test("data/tests/test37.asm")); }
    @Test public void test38() throws IOException { Assert.assertEquals(4, test("data/tests/test38.asm")); }
    @Test public void test39() throws IOException { Assert.assertEquals(0, test("data/tests/test39.asm")); }
    @Test public void test40() throws IOException { Assert.assertEquals(9, test("data/tests/test40.asm")); }
    @Test public void test41() throws IOException { Assert.assertEquals(0, test("data/tests/test41.asm")); }

    private int test(String inputFile) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-popatterns","data/pbo-patterns-size.txt"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        // Make sure we don't lose any labels:
        List<SourceConstant> labelsBefore = new ArrayList<>();
        List<SourceConstant> labelsAfter = new ArrayList<>();
        for(SourceFile f:code.getSourceFiles()) {
            for(SourceStatement s:f.getStatements()) {
                if (s.label != null && s.label.isLabel()) {
                    labelsBefore.add(s.label);
                }
            }
        }
        
        PatternBasedOptimizer.OptimizationResult r = pbo.optimize(code);

        for(SourceFile f:code.getSourceFiles()) {
            for(SourceStatement s:f.getStatements()) {
                if (s.label != null && s.label.isLabel()) {
                    labelsAfter.add(s.label);
                }
            }
        }
        
        if (labelsBefore.size() != labelsAfter.size()) {
            System.out.println("We lost labels!");
            return -1;  // some wrong value
        }
        
        AnnotatedSourceCodeGenerator scg = new AnnotatedSourceCodeGenerator(config);

        String result = scg.sourceFileString(code.getMain(), code);
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");
        for(String symbol:code.getSymbols()) {
            SourceConstant sc = code.getSymbol(symbol);
            System.out.println(sc.name + ": " + sc.getValue(code, true));
        }
        System.out.println("--------------------------------------\n");

        return r.bytesSaved;
    }
}

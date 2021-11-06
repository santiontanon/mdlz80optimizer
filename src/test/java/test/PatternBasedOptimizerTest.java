/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import cl.MDLConfig;
import cl.OptimizationResult;
import code.CodeBase;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
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

    @Test public void test1() throws IOException { test("data/potests/test1.asm", null, null, "size",  4, 9, 9); }
    @Test public void test2() throws IOException { test("data/potests/test2.asm", null, null, "size",  4, 9, 9); }
    @Test public void test3() throws IOException { test("data/potests/test3.asm", null, null, "size",  1, -2, -2); }
    @Test public void test4() throws IOException { test("data/potests/test4.asm", null, null, "size",  1, -2, -2); }
    @Test public void test5() throws IOException { test("data/potests/test5.asm", null, null, "size",  1, -2, -2); }
    @Test public void test6() throws IOException { test("data/potests/test6.asm", null, null, "size",  7, 18, 23); }
    @Test public void test7() throws IOException { test("data/potests/test7.asm", null, null, "size",  2, 8, 8); }
    @Test public void test8() throws IOException { test("data/potests/test8.asm", null, null, "size",  5, 29, 29); }
    @Test public void test9() throws IOException { test("data/potests/test9.asm", null, null, "size",  0, 0, 0); }
    @Test public void test10() throws IOException { test("data/potests/test10.asm", null, null, "size",  2, 10, 10); }
    @Test public void test11() throws IOException { test("data/potests/test11.asm", null, null, "size",  4, 8, 8); }
    @Test public void test12() throws IOException { test("data/potests/test12.asm", null, null, "size",  3, 0, 0); }
    @Test public void test13() throws IOException { test("data/potests/test13.asm", null, null, "size",  1, -2, -2); }
    @Test public void test14() throws IOException { test("data/potests/test14.asm", null, null, "size",  2, 3, 3); }
    @Test public void test15() throws IOException { test("data/potests/test15.asm", null, null, "size",  3, 7, 7); }
    @Test public void test16() throws IOException { test("data/potests/test16.asm", null, null, "size",  8, 33, 33); }
    @Test public void test17() throws IOException { test("data/potests/test17.asm", null, null, "size",  7, 22, 22); }
    @Test public void test18() throws IOException { test("data/potests/test18.asm", null, null, "size",  7, 18, 18); }
    @Test public void test19() throws IOException { test("data/potests/test19.asm", null, null, "size",  3, 8, 8); }
    @Test public void test20() throws IOException { test("data/potests/test20.asm", null, null, "size",  9, 4, 19); }
    @Test public void test21() throws IOException { test("data/potests/test21.asm", null, null, "size",  4, 9, 9); }
    @Test public void test22() throws IOException { test("data/potests/test22.asm", null, null, "size",  3, 6, 6); }
    @Test public void test23() throws IOException { test("data/potests/test23.asm", null, null, "size",  2, 3, 3); }
    @Test public void test24() throws IOException { test("data/potests/test24.asm", null, null, "size",  1, -2, -2); }
    @Test public void test25() throws IOException { test("data/potests/test25.asm", null, null, "size",  1, -2, -2); }
    @Test public void test26() throws IOException { test("data/potests/test26.asm", null, null, "size",  1, -2, -2); }
    @Test public void test27() throws IOException { test("data/potests/test27.asm", null, null, "size",  5, 14, 14); }
    @Test public void test28() throws IOException { test("data/potests/test28.asm", null, null, "size",  8, 37, 37); }
    @Test public void test29() throws IOException { test("data/potests/test29.asm", null, null, "size",  3, 4, 4); }
    @Test public void test30() throws IOException { test("data/potests/test30.asm", null, null, "size",  3, 6, 6); }
    @Test public void test31() throws IOException { test("data/potests/test31.asm", null, null, "size",  8, 32, 32); }
    @Test public void test32() throws IOException { test("data/potests/test32.asm", null, null, "size",  5, 10, 10); }
    @Test public void test33() throws IOException { test("data/potests/test33.asm", null, null, "size",  3, -6, -6); }
    @Test public void test34() throws IOException { test("data/potests/test34.asm", null, null, "size",  1, 5, 5); }
    @Test public void test35() throws IOException { test("data/potests/test35.asm", null, null, "size",  1, 8, 3); }
    @Test public void test36() throws IOException { test("data/potests/test36.asm", null, null, "size",  7, 70, 70); }
    @Test public void test37() throws IOException { test("data/potests/test37.asm", null, null, "size",  3, 13, 13); }
    @Test public void test38() throws IOException { test("data/potests/test38.asm", null, null, "size",  4, 24, 24); }
    @Test public void test39() throws IOException { test("data/potests/test39.asm", null, null, "size",  0, 0, 0); }
    @Test public void test40() throws IOException { test("data/potests/test40.asm", null, null, "size",  9, 54, 54); }
    @Test public void test41() throws IOException { test("data/potests/test41.asm", null, null, "size",  0, 0, 0); }
    @Test public void test42() throws IOException { test("data/potests/test42.asm", null, null, "size",  2, 4, 4); }
    @Test public void test43() throws IOException { test("data/potests/test43.asm", null, null, "size",  0, 2, 2); }
    @Test public void test44() throws IOException { test("data/potests/test44.asm", null, null, "size",  5, 26, 26); }
    @Test public void test45() throws IOException { test("data/potests/test45.asm", null, null, "size",  4, 22, 22); }
    @Test public void test46() throws IOException { test("data/potests/test46.asm", null, null, "size",  1, 5, 5); }
    @Test public void test47() throws IOException { test("data/potests/test47.asm", null, null, "size",  2, 8, 8); }
    @Test public void test48() throws IOException { test("data/potests/test48.asm", null, null, "size",  1, 5, 5); }
    @Test public void test49() throws IOException { test("data/potests/test49.asm", null, null, "size",  1, 7, 7); }
    @Test public void test50() throws IOException { test("data/potests/test50.asm", null, null, "size",  0, 0, 0); }
    @Test public void test51() throws IOException { test("data/potests/test51.asm", null, null, "speed",  -1, 2, 2); }
    @Test public void test51cpc() throws IOException { test("data/potests/test51.asm", null, "z80cpc", "speed",  1, 0, 0); }
    @Test public void test52() throws IOException { test("data/potests/test52.asm", null, null, "size",  1, 3, 3); }
    @Test public void test52sdcc() throws IOException { test("data/potests/test52sdcc.asm", "sdcc", null, "size",  0, 0, 0); }
    @Test public void test54() throws IOException { test("data/potests/test54.asm", null, null, "size",  2, 4, 4); }
    @Test public void test54ldo() throws IOException { testWithoutLabelDependentOptimizations("data/potests/test54.asm", null, null, "size",  0, 0, 0); }
    @Test public void test55() throws IOException { test("data/potests/test55.asm", null, null, "size",  2, 8, 8); }
    @Test public void test56() throws IOException { test("data/potests/test56.asm", null, null, "size",  3, 19, 19); }
    @Test public void test57() throws IOException { test("data/potests/test57.asm", null, null, "size",  3, 13, 8); }
    @Test public void test58() throws IOException { test("data/potests/test58.asm", null, null, "size",  8, 36, 36); }
    @Test public void test59() throws IOException { test("data/potests/test59.asm", null, null, "size",  2, 12, 12); }
    @Test public void test60() throws IOException { test("data/potests/test60.asm", null, null, "size",  25, 119, 119); }
    @Test public void test61() throws IOException { test("data/potests/test61.asm", null, null, "size",  2, 8, 8); }
    @Test public void test62() throws IOException { test("data/potests/test62.asm", null, null, "size",  22, 135, 135); }
    @Test public void test63() throws IOException { test("data/potests/test63.asm", null, null, "size",  8, 41, 41); }
    @Test public void test64() throws IOException { test("data/potests/test64.asm", null, null, "size",  1, 5, 5); }
    @Test public void test65() throws IOException { test("data/potests/test65.asm", null, null, "size",  1, 3, 3); }
    @Test public void test66() throws IOException { test("data/potests/test66.asm", null, null, "size",  2, 16, 16); }

    
    private void test(String inputFile, String dialect, String cpu, String target, int expectedSavedBytes, int expectedSavedTime1, int expectedSavedTime2) throws IOException
    {
        if (dialect == null) {
            if (cpu == null) {
                Assert.assertTrue(config.parseArgs(inputFile, "-po", target));
            } else {
                Assert.assertTrue(config.parseArgs(inputFile, "-cpu", cpu, "-po", target));
            }
        } else {
            if (cpu == null) {
                Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect, "-po", target));
            } else {
                Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect, "-cpu", cpu,  "-po", target));
            }
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));        
        testInternal(expectedSavedBytes, expectedSavedTime1, expectedSavedTime2);
    }

    
    private void testWithoutLabelDependentOptimizations(String inputFile, String dialect, String cpu, String target, int expectedSavedBytes, int expectedSavedTime1, int expectedSavedTime2) throws IOException
    {
        if (dialect == null) {
            if (cpu == null) {
                Assert.assertTrue(config.parseArgs(inputFile, "-po-ldo", "-po", target));
            } else {
                Assert.assertTrue(config.parseArgs(inputFile, "-cpu", cpu, "-po-ldo", "-po", target));
            }
        } else {
            if (cpu == null) {
                Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect, "-po-ldo", "-po", target));
            } else {
                Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect, "-cpu", cpu,  "-po-ldo", "-po", target));
            }
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));        
        testInternal(expectedSavedBytes, expectedSavedTime1, expectedSavedTime2);
    }
    

    private void testInternal(int expectedSavedBytes, int expectedSavedTime1, int expectedSavedTime2) throws IOException
    {
        // Make sure we don't lose any labels:
        List<SourceConstant> labelsBefore = new ArrayList<>();
        List<SourceConstant> labelsAfter = new ArrayList<>();
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.label != null && s.label.isLabel()) {
                    labelsBefore.add(s.label);
                }
            }
        }
                
        OptimizationResult r = pbo.optimize(code);

        // check the intergrity of patterns:
        Assert.assertTrue(pbo.checkPatternIntegrity());
        
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.label != null && s.label.isLabel()) {
                    labelsAfter.add(s.label);
                }
            }
        }
        
        Assert.assertEquals(labelsBefore.size(), labelsAfter.size());
        
        AnnotatedSourceCodeGenerator scg = new AnnotatedSourceCodeGenerator(config);

        String result = scg.sourceFileString(code.outputs.get(0).main, code);
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");
        for(String symbol:code.getSymbols()) {
            SourceConstant sc = code.getSymbol(symbol);
            System.out.println(sc.name + ": " + sc.getValue(code, true));
        }
        System.out.println("--------------------------------------\n");

        Assert.assertEquals("r.bytesSaved", expectedSavedBytes, r.bytesSaved);
        Assert.assertEquals("r.timeSavings[0]", expectedSavedTime1, r.timeSavings[0]);
        Assert.assertEquals("r.timeSavings[1]", expectedSavedTime2, r.timeSavings[1]);
    }
}

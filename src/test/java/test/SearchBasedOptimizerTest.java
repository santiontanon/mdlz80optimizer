/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import workers.SourceCodeGenerator;
import workers.searchopt.SearchBasedOptimizer;

/**
 *
 * @author santi
 */
public class SearchBasedOptimizerTest {
    private final MDLConfig config;
    private final CodeBase code;
    private final SearchBasedOptimizer sbo;
    
    public SearchBasedOptimizerTest() {
        config = new MDLConfig();
        sbo = new SearchBasedOptimizer(config);
        config.registerWorker(sbo);
        code = new CodeBase(config);
    }

    @Test public void test0() throws IOException { test("data/searchtests/test0.txt", "data/searchtests/test0-expected.asm"); }
    @Test public void test1() throws IOException { test("data/searchtests/test1.txt", "data/searchtests/test1-expected.asm"); }
    @Test public void test1b() throws IOException { test("data/searchtests/test1b.txt", "data/searchtests/test1b-expected.asm"); }
    @Test public void test2() throws IOException { test("data/searchtests/test2.txt", "data/searchtests/test2-expected.asm"); }
    @Test public void test2b() throws IOException { test("data/searchtests/test2b.txt", null); }
    @Test public void test2c() throws IOException { test("data/searchtests/test2c.txt", "data/searchtests/test2c-expected.asm"); }
    @Test public void test3() throws IOException { test("data/searchtests/test3.txt", "data/searchtests/test3-expected.asm"); }
    @Test public void test4() throws IOException { test("data/searchtests/test4.txt", "data/searchtests/test4-expected.asm"); }
    @Test public void test5() throws IOException { test("data/searchtests/test5.txt", "data/searchtests/test5-expected.asm"); }
    @Test public void test5b() throws IOException { test("data/searchtests/test5b.txt", "data/searchtests/test5b-expected.asm"); }

    // These are larger tests (that are a bit slow, and thus
    // are deactivated by default for quick building):
//     Current version: 0.115 sec (1906 solutions tested)
//    @Test public void testLShift9() throws IOException { test("data/searchtests/test-large1.txt", "data/searchtests/test-large1-expected.asm"); }
//     Current version: 0.167 sec (38443 solutions tested)
//    @Test public void testLShift10() throws IOException { test("data/searchtests/test-large2.txt", "data/searchtests/test-large2-expected.asm"); }
    // Current version: 0.157 sec (13715 solutions tested)
//    @Test public void testLShift10b() throws IOException { test("data/searchtests/test-large2b.txt", "data/searchtests/test-large2b-expected.asm"); }
    // Current version: 0.254 sec (50503 solutions tested)
//    @Test public void testLShift10c() throws IOException { test("data/searchtests/test-large2c.txt", "data/searchtests/test-large2c-expected.asm"); }
    // Current version: 0.877 sec (1546805 solutions tested)
//    @Test public void testLShift11() throws IOException { test("data/searchtests/test-large3.txt", "data/searchtests/test-large3-expected.asm"); }
    // Current version: 22.889 sec (51859338 solutions tested)
//    @Test public void testLShift12() throws IOException { test("data/searchtests/test-large4.txt", "data/searchtests/test-large4-expected.asm"); }
    
    private void test(String inputFile, String expectedOutput) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile, "-so"));
        if (expectedOutput == null) {
            Assert.assertFalse(
                    "Solution found, when there should not have been one for specification file: " + inputFile,
                    sbo.work(code));
        } else {
            Assert.assertTrue(
                    "Could not generate code for specification file: " + inputFile,
                    sbo.work(code));
            // Compare standard assembler generation:
            SourceCodeGenerator scg = new SourceCodeGenerator(config);
            Assert.assertFalse(code.outputs.isEmpty());
            String result = scg.outputFileString(code.outputs.get(0), code);
            Assert.assertTrue(GenerationTest.compareOutputs(result, expectedOutput));
        }
    }    
}

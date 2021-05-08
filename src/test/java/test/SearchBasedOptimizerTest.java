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
    @Test public void test6() throws IOException { test("data/searchtests/test6.txt", "data/searchtests/test6-expected.asm"); }
    @Test public void test7() throws IOException { test("data/searchtests/test7.txt", "data/searchtests/test7-expected.asm"); }
    @Test public void test8() throws IOException { test("data/searchtests/test8.txt", "data/searchtests/test8-expected.asm"); }
    
    @Test public void testFlags1() throws IOException { test("data/searchtests/test-flags1.txt", "data/searchtests/test-flags1-expected.asm"); }

    @Test public void testLShift9() throws IOException { test("data/searchtests/test-large1.txt", "data/searchtests/test-large1-expected.asm"); }
    @Test public void testLShift9size() throws IOException { test("data/searchtests/test-large1.txt", "data/searchtests/test-large1-size-expected.asm", "-so-size"); }
    @Test public void testLShift9time() throws IOException { test("data/searchtests/test-large1.txt", "data/searchtests/test-large1-time-expected.asm", "-so-time"); }

    // These are larger tests (that are a bit slow, and thus are deactivated by default for quick building):
    // Current version: 0.172 sec (19206 solutions tested)
//    @Test public void testLShift10() throws IOException { test("data/searchtests/test-large2.txt", "data/searchtests/test-large2-expected.asm"); }
    // Current version: 0.189 sec (8292 solutions tested)
//    @Test public void testLShift10b() throws IOException { test("data/searchtests/test-large2b.txt", "data/searchtests/test-large2b-expected.asm"); }
    // Current version: 0.263 sec (32948 solutions tested)
//    @Test public void testLShift10c() throws IOException { test("data/searchtests/test-large2c.txt", "data/searchtests/test-large2c-expected.asm"); }
    // Current version: 0.344 sec (851313 solutions tested)
//    @Test public void testLShift11() throws IOException { test("data/searchtests/test-large3.txt", "data/searchtests/test-large3-expected.asm"); }
    // Current version: 0.481 sec (1098178 solutions tested)
//    @Test public void testMin() throws IOException { test("data/searchtests/test-min.txt", "data/searchtests/test-min-expected.asm"); }
    // Current version: 0.829 sec (4665982 solutions tested)
//    @Test public void testSort() throws IOException { test("data/searchtests/test-sort.txt", "data/searchtests/test-sort-expected.asm"); }
//     Current version: 3.067 sec (24431654 solutions tested)
//    @Test public void testLShift12() throws IOException { test("data/searchtests/test-large4.txt", "data/searchtests/test-large4-expected.asm"); }
    // Current version: 86.859 sec (742011495 solutions tested)
//    @Test public void testLShift13() throws IOException { test("data/searchtests/test-large5.txt", "data/searchtests/test-large5-expected.asm"); }
    
    
    private void test(String inputFile, String expectedOutput) throws IOException
    {
        test(inputFile, expectedOutput, null);
    }
    
            
    private void test(String inputFile, String expectedOutput, String searchTypeArg) throws IOException
    {
        if (searchTypeArg != null) {
            Assert.assertTrue(config.parseArgs(inputFile, searchTypeArg));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-so"));
        }
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

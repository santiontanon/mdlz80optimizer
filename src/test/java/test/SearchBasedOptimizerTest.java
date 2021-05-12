/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.junit.Assert;
import org.junit.Test;
import util.Resources;
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
    // Current version: 0.172 sec (13326 solutions tested)
//    @Test public void testLShift10() throws IOException { test("data/searchtests/test-large2.txt", "data/searchtests/test-large2-expected.asm"); }
    // Current version: 0.189 sec (5676 solutions tested)
//    @Test public void testLShift10b() throws IOException { test("data/searchtests/test-large2b.txt", "data/searchtests/test-large2b-expected.asm"); }
    // Current version: 0.263 sec (20856 solutions tested)
//    @Test public void testLShift10c() throws IOException { test("data/searchtests/test-large2c.txt", "data/searchtests/test-large2c-expected.asm"); }
    // Current version: 0.344 sec (605147 solutions tested)
//    @Test public void testLShift11() throws IOException { test("data/searchtests/test-large3.txt", "data/searchtests/test-large3-expected.asm"); }
    // Current version: 0.481 sec (1045273 solutions tested)
//    @Test public void testMin() throws IOException { test("data/searchtests/test-min.txt", "data/searchtests/test-min-expected.asm"); }
    // Current version: 0.738 sec (1100248 solutions tested 1-thread)
//    @Test public void testSort() throws IOException { test("data/searchtests/test-sort.txt", "data/searchtests/test-sort-expected.asm"); }
    // Current version: 2.63 sec (18967141 solutions tested 1-thread)
//    @Test public void testLShift12() throws IOException { test("data/searchtests/test-large4.txt", "data/searchtests/test-large4-expected.asm"); }
    // Current version: 77.379 sec (613206638 solutions tested 1-thread)
//    @Test public void testLShift13() throws IOException { test("data/searchtests/test-large5.txt", "data/searchtests/test-large5-expected.asm"); }
    // Current version: 186.539 sec (4231914354 solutions tested, 8-threads)
//    @Test public void testLShift13Hard() throws IOException { test("data/searchtests/test-large6.txt", "data/searchtests/test-large6-expected.asm"); }
    // Current version: - sec (- solutions tested)
//    @Test public void testLShift13HardSpeed() throws IOException { test("data/searchtests/test-large6.txt", "data/searchtests/test-large6-expected.asm", "-so-time"); }
    
    
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
            Assert.assertTrue(compareOutputsWithAlternatives(result, expectedOutput));
        }
    }    
    
    
    public static boolean compareOutputsWithAlternatives(String result, String expectedOutputFile) throws IOException
    {
        List<String> lines = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(result, "\n");
        while(st.hasMoreTokens()) {
            lines.add(st.nextToken().trim());
        }
        
        List<List<String>> expectedAlternatives = new ArrayList<>();
        BufferedReader br = Resources.asReader(expectedOutputFile);
        List<String> expectedAlternative = new ArrayList<>();
        while(true) {
            String line = br.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.length() > 0) {
                if (line.contains("----")) {
                    expectedAlternatives.add(expectedAlternative);
                    expectedAlternative = new ArrayList<>();
                } else {
                    expectedAlternative.add(line);
                }
            }
        }
        if (!expectedAlternative.isEmpty() || expectedAlternatives.isEmpty()) {
            expectedAlternatives.add(expectedAlternative);
        }
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");
        
        for(List<String> expected : expectedAlternatives) {
            boolean match = true;
            for(int i = 0;i<Math.max(lines.size(), expected.size());i++) {
                String line = lines.size() > i ? lines.get(i):"";
                String expectedLine = expected.size() > i ? expected.get(i):"";
                if (!line.equals(expectedLine)) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        
        return false;
    }       
}

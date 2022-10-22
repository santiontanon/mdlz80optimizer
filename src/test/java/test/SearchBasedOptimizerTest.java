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

    @Test public void test1_2() throws IOException { test("data/searchtests/opt-test1.asm", "data/searchtests/opt-test1-expected-b2.asm", "ops", 2); }
    @Test public void test1_3() throws IOException { test("data/searchtests/opt-test1.asm", 
            new String[]{"data/searchtests/opt-test1-expected-b3.asm", "data/searchtests/opt-test1-expected2-b3.asm", "data/searchtests/opt-test1-expected3-b3.asm"}, "ops", 3); }
    @Test public void test2() throws IOException { test("data/searchtests/opt-test2.asm", 
            new String[]{"data/searchtests/opt-test2-expected.asm", "data/searchtests/opt-test2-expected2.asm"}); }
    @Test public void test2_size() throws IOException { test("data/searchtests/opt-test2.asm", 
            new String[]{"data/searchtests/opt-test2-expected.asm", "data/searchtests/opt-test2-expected2.asm"}, "size", 2); }
    @Test public void test2_speed() throws IOException { test("data/searchtests/opt-test2.asm", 
            new String[]{"data/searchtests/opt-test2-expected.asm", "data/searchtests/opt-test2-expected2.asm"}, "speed", 2); }
    @Test public void test3() throws IOException { test("data/searchtests/opt-test3.asm", new String[]{"data/searchtests/opt-test3-expected.asm"}); }
    @Test public void test4() throws IOException { test("data/searchtests/opt-test4.asm", new String[]{"data/searchtests/opt-test4-expected.asm"}); }
    @Test public void test5() throws IOException { test("data/searchtests/opt-test5.asm", new String[]{"data/searchtests/opt-test5-expected.asm"}); }
    @Test public void test6() throws IOException { test("data/searchtests/opt-test6.asm", 
            new String[]{"data/searchtests/opt-test6-expected.asm", "data/searchtests/opt-test6-expected2.asm"}); }
    @Test public void test7() throws IOException { test("data/searchtests/opt-test7.asm", new String[]{"data/searchtests/opt-test7-expected.asm"}); }
    @Test public void test8() throws IOException { test("data/searchtests/opt-test8.asm", new String[]{"data/searchtests/opt-test8-expected.asm"}); }
    @Test public void test9() throws IOException { test("data/searchtests/opt-test9.asm", new String[]{"data/searchtests/opt-test9-expected.asm"}); }
    @Test public void test10() throws IOException { test("data/searchtests/opt-test10.asm", 
              new String[]{"data/searchtests/opt-test10-expected.asm", "data/searchtests/opt-test10-expected2.asm"}); }
    @Test public void test11() throws IOException { test("data/searchtests/opt-test11.asm", 
              new String[]{"data/searchtests/opt-test11-expected.asm", "data/searchtests/opt-test11-expected2.asm"}); }
    @Test public void test12() throws IOException { test("data/searchtests/opt-test12.asm", 
              new String[]{"data/searchtests/opt-test12-expected.asm"}); }
    @Test public void test13() throws IOException { test("data/searchtests/opt-test13.asm", 
              new String[]{"data/searchtests/opt-test13-expected-opssafe.asm"}, "opssafe", 2); }
    @Test public void test13b() throws IOException { test("data/searchtests/opt-test13.asm", 
              new String[]{"data/searchtests/opt-test13-expected-ops.asm"}, "ops", 2); }
    @Test public void test14() throws IOException { test("data/searchtests/opt-test14.asm", 
              new String[]{"data/searchtests/opt-test14-expected.asm", "data/searchtests/opt-test14-expected2.asm"}); }
    @Test public void test15() throws IOException { testZ80Next("data/searchtests/opt-test15.asm", 
              new String[]{"data/searchtests/opt-test15-expected.asm"}); }    
    // This is a test to optimize jumps. I decided not to support blocks with jumps, since they
    // are problematic, so, this test is commented out for now (it will not pass):
//    @Test public void test16() throws IOException { test("data/searchtests/opt-test16.asm", 
//              new String[]{"data/searchtests/opt-test16-expected.asm"}); }
    @Test public void test17() throws IOException { testZ80Next("data/searchtests/opt-test17.asm", 
              new String[]{"data/searchtests/opt-test17-expected.asm"}); }
    @Test public void test18() throws IOException { testZ80Next("data/searchtests/opt-test18.asm", 
              new String[]{"data/searchtests/opt-test18-expected.asm"}); }

    
            
    private void test(String inputFile, String expectedOutputFiles[]) throws IOException
    {
        test(inputFile, expectedOutputFiles, null, 2);
    }


    private void test(String inputFile, String expectedOutputFile, String searchTypeArg, int blockSize) throws IOException
    {
        test(inputFile, new String[]{expectedOutputFile}, searchTypeArg, blockSize);        
    }    

    
    private void test(String inputFile, String expectedOutputFiles[], String searchTypeArg, int blockSize) throws IOException
    {
        if (searchTypeArg != null) {
            Assert.assertTrue(config.parseArgs(inputFile, "-so-opt", searchTypeArg, "-so-blocksize", blockSize + ""));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-so-opt", "-so-blocksize", blockSize + ""));
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));        
        Assert.assertTrue(sbo.work(code));
        
        SourceCodeGenerator scg = new SourceCodeGenerator(config);        
        String result = scg.outputFileString(code.outputs.get(0), code);
        boolean anyMatch = false;
        for(String expectedOutputFile:expectedOutputFiles) {
            if (GenerationTest.compareOutputs(result, expectedOutputFile)) {
                anyMatch = true;
                break;
            }
        }
        Assert.assertTrue(anyMatch);
    }       


    private void testZ80Next(String inputFile, String expectedOutputFiles[]) throws IOException
    {
        int blockSize = 2;
        Assert.assertTrue(config.parseArgs(inputFile, "-cpu", "z80next", "-so-opt", "-so-blocksize", blockSize + ""));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));        
        Assert.assertTrue(sbo.work(code));
        
        SourceCodeGenerator scg = new SourceCodeGenerator(config);        
        String result = scg.outputFileString(code.outputs.get(0), code);
        boolean anyMatch = false;
        for(String expectedOutputFile:expectedOutputFiles) {
            if (GenerationTest.compareOutputs(result, expectedOutputFile)) {
                anyMatch = true;
                break;
            }
        }
        Assert.assertTrue(anyMatch);
    }

    
}

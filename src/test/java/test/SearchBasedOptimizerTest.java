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

//    @Test public void test1_2() throws IOException { test("data/searchtests/opt-test1.asm", "data/searchtests/opt-test1-expected-b2.asm", "ops", 2); }
//    @Test public void test1_3() throws IOException { test("data/searchtests/opt-test1.asm", 
//            new String[]{"data/searchtests/opt-test1-expected-b3.asm", "data/searchtests/opt-test1-expected2-b3.asm"}, "ops", 3); }
//    @Test public void test2() throws IOException { test("data/searchtests/opt-test2.asm", 
//            new String[]{"data/searchtests/opt-test2-expected.asm", "data/searchtests/opt-test2-expected2.asm"}); }
//    @Test public void test2_size() throws IOException { test("data/searchtests/opt-test2.asm", 
//            new String[]{"data/searchtests/opt-test2-expected.asm", "data/searchtests/opt-test2-expected2.asm"}, "size", 2); }
//    @Test public void test2_speed() throws IOException { test("data/searchtests/opt-test2.asm", 
//            new String[]{"data/searchtests/opt-test2-expected.asm", "data/searchtests/opt-test2-expected2.asm"}, "speed", 2); }
//    @Test public void test3() throws IOException { test("data/searchtests/opt-test3.asm", new String[]{"data/searchtests/opt-test3-expected.asm"}); }
    @Test public void test4() throws IOException { test("data/searchtests/opt-test4.asm", new String[]{"data/searchtests/opt-test4-expected.asm"}); }

    
    private void test(String inputFile, String expectedOutput) throws IOException
    {
        test(inputFile, new String[]{expectedOutput}, null, 2);
    }
    
            
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
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));        
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

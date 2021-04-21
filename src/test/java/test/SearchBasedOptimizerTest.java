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

    @Test public void test1() throws IOException { test("data/searchtests/test1.txt", "data/searchtests/test1-expected.asm"); }

    
    private void test(String inputFile, String expectedOutput) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile, "-so"));
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

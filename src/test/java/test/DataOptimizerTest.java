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
import workers.DataOptimizer;

/**
 *
 * @author santi
 */
public class DataOptimizerTest {

    private final MDLConfig config;
    private final CodeBase code;
    private final DataOptimizer worker;

    public DataOptimizerTest() {
        config = new MDLConfig();
        worker = new DataOptimizer(config);
        config.registerWorker(worker);
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { test("data/dotests/test1.asm", 1, 4); }
    @Test public void test2() throws IOException { test("data/dotests/test2.asm", 1, 4); }
    @Test public void test3() throws IOException { test("data/dotests/test3.asm", 1, 4); }
    @Test public void test4() throws IOException { test("data/dotests/test4.asm", 1, 4); }
    @Test public void test5() throws IOException { test("data/dotests/test5.asm", 1, 5); }
    @Test public void test6() throws IOException { test("data/dotests/test6.asm", 1, 6); }
    @Test public void test7() throws IOException { test("data/dotests/test7.asm", 1, 5); }
    @Test public void test8() throws IOException { test("data/dotests/test8.asm", 0, 0); }

    
    private void test(String inputFile, int nDataOptimizations, int expectedSavedBytes) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile, "-do"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));        
        testInternal(nDataOptimizations, expectedSavedBytes);
    }
    

    private void testInternal(int nDataOptimizations, int expectedSavedBytes) throws IOException
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
                
        worker.work(code);
        OptimizationResult r = config.optimizerStats;
        
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.label != null && s.label.isLabel()) {
                    labelsAfter.add(s.label);
                }
            }
        }
        
        Assert.assertEquals(labelsBefore.size(), labelsAfter.size());
        Integer nOptimizations = r.optimizerSpecificStats.get(DataOptimizer.DATA_OPTIMIZER_OPTIMIZATIONS_CODE);
        if (nOptimizations == null) nOptimizations = 0;
        Integer nPotentialBytes = r.optimizerSpecificStats.get(DataOptimizer.DATA_OPTIMIZER_POTENTIAL_BYTES_CODE);
        if (nPotentialBytes == null) nPotentialBytes = 0;
        Assert.assertEquals("r.nDataOptimizations", nDataOptimizations, (int)nOptimizations);
        Assert.assertEquals("r.nPotentialBytes", expectedSavedBytes, (int)nPotentialBytes);
    }
}

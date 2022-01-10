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

/**
 *
 * @author santi
 */
public class GlassTest {

    private final MDLConfig config;
    private final CodeBase code;

    public GlassTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/glass-irp.asm",
                                                                          "data/generationtests/glass-irp-expected.asm", null)); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/glass-proc.asm",
                                                                          "data/generationtests/glass-proc-expected.asm", null)); }
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/glass-macroproc.asm",
                                                                          "data/generationtests/glass-macroproc-expected.asm",
                                                                          "data/generationtests/glass-macroproc-dialect-expected.asm")); }
    @Test public void test4() throws IOException { Assert.assertTrue(test("data/generationtests/glass-proc2.asm",
                                                                          "data/generationtests/glass-proc2-expected.asm", null)); }
    @Test public void test5() throws IOException { Assert.assertTrue(test("data/generationtests/glass-labels.asm",
                                                                          "data/generationtests/glass-labels-expected.asm", null)); }

    private boolean test(String inputFile, String expectedOutputFile, String expectedDialectOutputFile) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-dialect","glass"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));

        // Compare standard assembler generation:
        SourceCodeGenerator scg = new SourceCodeGenerator(config);
        String result = scg.outputFileString(code.outputs.get(0), code);
        if (!GenerationTest.compareOutputs(result, expectedOutputFile)) return false;

        // Compare dialect assembler generation:
        if (expectedDialectOutputFile != null) {
            SourceCodeGenerator scg_dialect = new SourceCodeGenerator(config);
            scg_dialect.mimicTargetDialect = true;
            String resultDialect = scg_dialect.outputFileString(code.outputs.get(0), code);
            if (!GenerationTest.compareOutputs(resultDialect, expectedDialectOutputFile)) return false;
        }
        
        return true;
    }    
   
}

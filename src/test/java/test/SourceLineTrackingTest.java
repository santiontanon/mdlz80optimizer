/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.junit.Assert;
import org.junit.Test;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class SourceLineTrackingTest {

    private final MDLConfig mdlConfig;
    private final CodeBase codeBase;

    public SourceLineTrackingTest() {
        mdlConfig = new MDLConfig();
        codeBase = new CodeBase(mdlConfig);
    }

    @Test public void test1() throws IOException { 
        Assert.assertTrue(test(
                "data/tests/test1.asm", 
                new String[]{"INFO: Pattern-based optimization in data/tests/test1.asm#6: Replace cp 0 with or a (1 bytes, 3 t-states saved)",
                             "INFO: Pattern-based optimization in data/tests/test1.asm#15: Remove unused ld a,? (2 bytes, 8 t-states saved)"})); 
    }
    @Test public void test29() throws IOException { 
        Assert.assertTrue(test(
                "data/tests/test29.asm", 
                new String[]{"INFO: Pattern-based optimization in data/tests/test29-include.asm#4 (expanded from data/tests/test29.asm#6): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)",
                             "INFO: Pattern-based optimization in data/tests/test29-include.asm#4 (expanded from data/tests/test29.asm#8): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)"})); 
    }

    private boolean test(String inputFile, String expectedOutputLines[]) throws IOException
    {
        Assert.assertTrue(mdlConfig.parseArgs(inputFile,"-dialect","glass"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                mdlConfig.codeBaseParser.parseMainSourceFile(mdlConfig.inputFile, codeBase));
        
        ByteArrayOutputStream optimizerOutput = new ByteArrayOutputStream();        
        mdlConfig.logger = new MDLLogger(MDLLogger.INFO, new PrintStream(optimizerOutput), System.err);
        PatternBasedOptimizer po = new PatternBasedOptimizer(mdlConfig);
        po.optimize(codeBase);
        
        String lines[] = optimizerOutput.toString().split("\n");
        for(String expectedOutputLine: expectedOutputLines) {
            boolean found = false;
            for(String line: lines) {
                if (line.equals(expectedOutputLine)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("Expected line '"+expectedOutputLine+"' not found in actual optimizer output!");
                return false;
            }
        }
        
        return true;
    }    
    
}

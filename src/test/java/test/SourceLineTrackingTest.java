/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import util.TextUtils;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class SourceLineTrackingTest {

    private final MDLConfig config;
    private final CodeBase code;

    public SourceLineTrackingTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException {
        // (uses wildcards to prevent test failure in Windows)
        optimizeAndLookFor("data/potests/test1.asm",
                "INFO: Pattern-based optimization in *test1.asm#6: Replace cp 0 with or a (1 bytes, 3 t-states saved)",
                "INFO: Pattern-based optimization in *test1.asm#15: Remove unused ld a,0 (2 bytes, 8 t-states saved)");
    }
    @Test public void test29() throws IOException {
        // (uses wildcards to prevent test failure in Windows)
        optimizeAndLookFor("data/potests/test29.asm",
                "INFO: Pattern-based optimization in *test29-include.asm#4 (expanded from *test29.asm#6): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)",
                "INFO: Pattern-based optimization in *test29-include.asm#4 (expanded from *test29.asm#8): Replace ld a,0 with xor a (1 bytes, 3 t-states saved)");
    }

    private void optimizeAndLookFor(String inputFile, String ... expectedOutputLines) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-dialect","glass"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        // Optimize:
        String lines[];
        try (ByteArrayOutputStream optimizerOutput = new ByteArrayOutputStream();
             PrintStream printStream = new PrintStream(optimizerOutput)) {

            config.logger = new MDLLogger(MDLLogger.INFO, printStream, System.err);
            PatternBasedOptimizer po = new PatternBasedOptimizer(config);
            po.optimize(code);

            printStream.flush();
            lines = optimizerOutput.toString().split("\n");
            System.out.println(optimizerOutput.toString());
        }

        // Look for expected output:
        for (String expectedOutputLine: expectedOutputLines) {
            Assert.assertTrue(
                    "Expected line '"+expectedOutputLine+"' not found in actual optimizer output!",
                    TextUtils.anyMatchesIgnoreCase(expectedOutputLine, lines));
        }
    }
}

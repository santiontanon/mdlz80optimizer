/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import workers.AnnotatedSourceCodeGenerator;
import workers.pattopt.PatternBasedOptimizer;

/**
 *
 * @author santi
 */
public class PotentialOptimizationsTest {

    private final MDLConfig config;
    private final CodeBase code;
    private final PatternBasedOptimizer pbo;

    public PotentialOptimizationsTest() {
        config = new MDLConfig();
        pbo = new PatternBasedOptimizer(config);
        config.registerWorker(pbo);
        code = new CodeBase(config);
    }

    @Test public void test43() throws IOException { Assert.assertEquals(1, test("data/tests/test43.asm")); }


    private int test(String inputFile) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        String lines[];
        try (ByteArrayOutputStream optimizerOutput = new ByteArrayOutputStream();
             PrintStream printStream = new PrintStream(optimizerOutput)) {

            config.logger = new MDLLogger(MDLLogger.INFO, printStream, System.err);
            PatternBasedOptimizer po = new PatternBasedOptimizer(config);
            po.logPotentialOptimizations = true;
            po.optimize(code);

            printStream.flush();
            lines = optimizerOutput.toString().split("\n");
        }
        
        int n_potential = 0;
        for(String line:lines) {
            System.out.println(line);
            if (line.startsWith("INFO: Potential optimization")) n_potential++;
        }
        
        AnnotatedSourceCodeGenerator scg = new AnnotatedSourceCodeGenerator(config);

        String result = scg.sourceFileString(code.getMain(), code);
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");
        for(String symbol:code.getSymbols()) {
            SourceConstant sc = code.getSymbol(symbol);
            System.out.println(sc.name + ": " + sc.getValue(code, true));
        }
        System.out.println("--------------------------------------\n");

        return n_potential;
    }
    
}

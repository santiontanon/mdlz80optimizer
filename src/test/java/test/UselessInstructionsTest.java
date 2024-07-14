/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package test;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Assert;
import org.junit.Test;
import workers.SourceCodeExecution;

/**
 *
 * @author santi
 */
public class UselessInstructionsTest {
    private final MDLConfig config;
    private final CodeBase code;
    private final SourceCodeExecution sce;

    public UselessInstructionsTest() {
        config = new MDLConfig();
        sce = new SourceCodeExecution(config);
        config.registerWorker(sce);
        code = new CodeBase(config);
    }
    
    @Test public void test1() throws Exception { Assert.assertTrue(test("data/executiontests/useless1.asm", 
                                                                        new String[]{"execution end: 0",
                                                                                     "Always useless: 1",
                                                                                     "Sometimes useless: 0"})); }
    @Test public void test2() throws Exception { Assert.assertTrue(test("data/executiontests/useless2.asm", 
                                                                        new String[]{"execution end: 1",
                                                                                     "Always useless: 2",
                                                                                     "Sometimes useless: 0"})); }
    @Test public void test3() throws Exception { Assert.assertTrue(test("data/executiontests/useless3.asm", 
                                                                        new String[]{"execution end: 0",
                                                                                     "Always useless: 0",
                                                                                     "Sometimes useless: 1"})); }
    @Test public void test4() throws Exception { Assert.assertTrue(test("data/executiontests/useless4.asm", 
                                                                        new String[]{"execution end: 0",
                                                                                     "Always useless: 0",
                                                                                     "Sometimes useless: 0"})); }
    @Test public void test5() throws Exception { Assert.assertTrue(test("data/executiontests/useless5.asm", 
                                                                        new String[]{"execution end: 0",
                                                                                     "Always useless: 0",
                                                                                     "Sometimes useless: 0"})); }
    @Test public void test6() throws Exception { Assert.assertTrue(test("data/executiontests/useless6.asm", 
                                                                        new String[]{"execution end: 0",
                                                                                     "Always useless: 1",
                                                                                     "Sometimes useless: 0"})); }
    @Test public void test7() throws Exception { Assert.assertTrue(test("data/executiontests/useless7.asm", 
                                                                        new String[]{"execution end: 1",
                                                                                     "Always useless: 0",
                                                                                     "Sometimes useless: 0"})); }
    @Test public void test8() throws Exception { Assert.assertTrue(test("data/executiontests/useless8.asm", 
                                                                        new String[]{"execution end: 1",
                                                                                     "Always useless: 0",
                                                                                     "Sometimes useless: 0"})); }

    private boolean test(String inputFile, String expectedLines[]) throws Exception
    {
        Assert.assertTrue(config.parseArgs(inputFile));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));

        // Compile the assembler code to binary:
        sce.startAddressUser = "start";
        sce.endAddressUser = "end";
        sce.trackUselessInstructions = true;
        sce.reportSometimesUselessInstructions = true;
        
        String output;
        String lines[];
        MDLLogger previousLogger = config.logger;
        try (ByteArrayOutputStream sceOutput = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(sceOutput)) {

            config.logger = new MDLLogger(MDLLogger.INFO, printStream, printStream);

            if (!sce.work(code)) {
                printStream.flush();
                output = sceOutput.toString();
                config.logger = previousLogger;
                config.info(output);
                return false;
            }
            
            printStream.flush();
            output = sceOutput.toString();
            lines = output.split("\n");
        }
        config.logger = previousLogger;
        // config.info(output);
        
        for(String expected: expectedLines) {
            boolean found = false;
            for(String line:lines) {
                if (line.contains(expected)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                config.error("Expected line '" + expected + "' not found in:\n" + output);
                return false;
            }
        }
        return true;
    }        
    
}

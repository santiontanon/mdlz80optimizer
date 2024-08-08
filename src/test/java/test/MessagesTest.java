/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Assert;
import org.junit.Test;
import util.ListOutputStream;
import util.Resources;
import workers.BinaryGenerator;

/**
 *
 * @author santi
 */
public class MessagesTest {
        
    private final MDLConfig config;
    private final CodeBase code;

    public MessagesTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws Exception { Assert.assertTrue(test("data/messagetests/include.asm", null,
                                                                        "data/messagetests/include-expected.txt")); }
    @Test public void test2() throws Exception { Assert.assertTrue(test("data/messagetests/undefined-symbol.asm", null,
                                                                        "data/messagetests/undefined-symbol-expected.txt")); }
        

    private boolean test(String inputFile, String dialect, String expectedMessagesFile) throws Exception
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile,"-dialect",dialect));
        }
        
        BufferedReader br = Resources.asReader(expectedMessagesFile);
        String expectedOutputLines[] = br.lines().toArray(String[]::new);
        
        // Optimize:
        String lines[];
        try (ByteArrayOutputStream optimizerOutput = new ByteArrayOutputStream();
             PrintStream printStream = new PrintStream(optimizerOutput)) {

            config.logger = new MDLLogger(MDLLogger.INFO, printStream, printStream);
            if (config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code)) {
                BinaryGenerator bg = new BinaryGenerator(config);
                ListOutputStream out = new ListOutputStream();
                bg.writeBytes(code.outputs.get(0).main, code, out, 0, true);                
            }
            
            printStream.flush();
            lines = optimizerOutput.toString().split("\n");
            System.out.println(optimizerOutput.toString());
        }

        // Look for expected output:
        for(int i = 0;i<Math.max(expectedOutputLines.length, lines.length);i++) {
            String expected = (i<expectedOutputLines.length ? expectedOutputLines[i]:"");
            String actual = (i<lines.length ? lines[i]:"");
            
            // remove the colors:
            actual = actual.replace(MDLLogger.ANSI_RESET, "");
            actual = actual.replace(MDLLogger.ANSI_YELLOW, "");
            actual = actual.replace(MDLLogger.ANSI_RED, "");
            Assert.assertTrue(
                    "\nexpected: '"+expected+"'\nbut found: '"+actual+"'",
                    // (trim to ignore line-ending differences)
                    expected.trim().equals(actual.trim()));
        }
        
        return true;
    }       

}

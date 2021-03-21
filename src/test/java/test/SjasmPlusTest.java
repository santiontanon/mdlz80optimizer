/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.junit.Assert;
import org.junit.Test;
import util.Resources;
import workers.SourceCodeGenerator;

/**
 *
 * @author santi
 */
public class SjasmPlusTest {

    private final MDLConfig config;
    private final CodeBase code;

    public SjasmPlusTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/sjasmplus-test1.asm",
                                                                          "data/generationtests/sjasmplus-test1-expected.asm")); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/sjasmplus-test2.asm",
                                                                          "data/generationtests/sjasmplus-test2-expected.asm")); }
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/sjasmplus-test3.asm",
                                                                          "data/generationtests/sjasmplus-test3-expected.asm")); }
    @Test public void test4() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-sjasmplus-abyte.asm",
                                                                          "data/generationtests/sjasm-sjasmplus-abyte-expected.asm")); }
    @Test public void test5() throws IOException { Assert.assertTrue(test("data/generationtests/sjasmplus-fake.asm",
                                                                          "data/generationtests/sjasmplus-fake-expected.asm")); }
    @Test public void test6() throws IOException { Assert.assertTrue(test("data/generationtests/sjasmplus-test6.asm",
                                                                          "data/generationtests/sjasmplus-test6-expected.asm")); }
    @Test public void test7() throws IOException { Assert.assertTrue(test("data/generationtests/sjasmplus-test7.asm",
                                                                          "data/generationtests/sjasmplus-test7-expected.asm")); }

    private boolean test(String inputFile, String expectedOutputFile) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-dialect","sjasmplus"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        SourceCodeGenerator scg = new SourceCodeGenerator(config);

        String result = scg.outputFileString(code.outputs.get(0), code);
        List<String> lines = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(result, "\n");
        while(st.hasMoreTokens()) {
            lines.add(st.nextToken().trim());
        }
        
        List<String> expectedLines = new ArrayList<>();
        BufferedReader br = Resources.asReader(expectedOutputFile);
        while(true) {
            String line = br.readLine();
            if (line == null) break;
            expectedLines.add(line.trim());
        }
        System.out.println("\n--------------------------------------");
        System.out.println(result);
        System.out.println("--------------------------------------\n");
        
        for(int i = 0;i<Math.max(lines.size(), expectedLines.size());i++) {
            String line = lines.size() > i ? lines.get(i):"";
            String expectedLine = expectedLines.size() > i ? expectedLines.get(i):"";
            if (!line.equals(expectedLine)) {
                System.out.println("Line " + i + " was expected to be:\n'" + expectedLine + "'\nbut was:\n'" + line + "'");
                return false;
            }
        }
        
        return true;
    }    
}

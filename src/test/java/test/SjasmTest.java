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
public class SjasmTest {

    private final MDLConfig config;
    private final CodeBase code;

    public SjasmTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-rept1.asm",
                                                                          "data/generationtests/sjasm-rept1-expected.asm")); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-macro.asm",
                                                                          "data/generationtests/sjasm-macro-expected.asm")); }
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-rept2.asm",
                                                                          "data/generationtests/sjasm-rept2-expected.asm")); }
    @Test public void test4() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-repeat1.asm",
                                                                          "data/generationtests/sjasm-repeat1-expected.asm")); }
    @Test public void test5() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-repeat2.asm",
                                                                          "data/generationtests/sjasm-repeat2-expected.asm")); }
    @Test public void test6() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-macro2.asm",
                                                                          "data/generationtests/sjasm-macro2-expected.asm")); }
    @Test public void test7() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-macro3.asm",
                                                                          "data/generationtests/sjasm-macro3-expected.asm")); }
    @Test public void test8() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-labels.asm",
                                                                          "data/generationtests/sjasm-labels-expected.asm")); }
    @Test public void test9() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-mixed.asm",
                                                                          "data/generationtests/sjasm-mixed-expected.asm")); }
    @Test public void test10() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-modules.asm",
                                                                           "data/generationtests/sjasm-modules-expected.asm")); }
    @Test public void test11() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-pletter.asm",
                                                                           "data/generationtests/sjasm-pletter-expected.asm")); }
    @Test public void test12() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-ifdef.asm",
                                                                           "data/generationtests/sjasm-ifdef-expected.asm")); }
    @Test public void test13() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-enhancedjr.asm",
                                                                           "data/generationtests/sjasm-enhancedjr-expected.asm")); }
    @Test public void test14() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-define.asm",
                                                                           "data/generationtests/sjasm-define-expected.asm")); }
    @Test public void test15() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-macro4.asm",
                                                                           "data/generationtests/sjasm-macro4-expected.asm")); }
    @Test public void test16() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-ifexists.asm",
                                                                           "data/generationtests/sjasm-ifexists-expected.asm")); }
    @Test public void test17() throws IOException { Assert.assertTrue(test("data/generationtests/sjasm-repeat.asm",
                                                                           "data/generationtests/sjasm-repeat-expected.asm")); }

    private boolean test(String inputFile, String expectedOutputFile) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-dialect","sjasm"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        SourceCodeGenerator scg = new SourceCodeGenerator(config);

        String result = scg.sourceFileString(code.getMain(), code);
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

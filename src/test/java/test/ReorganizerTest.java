/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.junit.Assert;
import org.junit.Test;
import util.ListOutputStream;
import util.Resources;
import workers.BinaryGenerator;
import workers.SourceCodeGenerator;
import workers.reorgopt.CodeReorganizer;

/**
 *
 * @author santi
 */
public class ReorganizerTest {

    private final MDLConfig config;
    private final CodeBase code;

    public ReorganizerTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws Exception { Assert.assertTrue(test("data/reorganizertests/test1.asm",
                                                                        "data/reorganizertests/test1-expected.asm",
                                                                        "data/reorganizertests/test1-expected.bin")); }
    @Test public void test2() throws Exception { Assert.assertTrue(test("data/reorganizertests/test2.asm",
                                                                        "data/reorganizertests/test2-expected.asm",
                                                                        "data/reorganizertests/test2-expected.bin")); }
    @Test public void test3() throws Exception { Assert.assertTrue(test("data/reorganizertests/test3.asm",
                                                                        "data/reorganizertests/test3-expected.asm",
                                                                        "data/reorganizertests/test3-expected.bin")); }
    @Test public void test4() throws Exception { Assert.assertTrue(test("data/reorganizertests/test4-file1.asm",
                                                                        "data/reorganizertests/test4-expected.asm",
                                                                        "data/reorganizertests/test4-expected.bin")); }
    @Test public void test5() throws Exception { Assert.assertTrue(test("data/reorganizertests/test5.asm",
                                                                        "data/reorganizertests/test5-expected.asm",
                                                                        "data/reorganizertests/test5-expected.bin")); }
    @Test public void test6() throws Exception { Assert.assertTrue(test("data/reorganizertests/test6.asm",
                                                                        "data/reorganizertests/test6-expected.asm",
                                                                        "data/reorganizertests/test6-expected.bin")); }
    @Test public void test7() throws Exception { Assert.assertTrue(test("data/reorganizertests/test7.asm",
                                                                        "data/reorganizertests/test7-expected.asm",
                                                                        "data/reorganizertests/test7-expected.bin")); }
    @Test public void test8() throws Exception { Assert.assertTrue(test("data/reorganizertests/test8.asm",
                                                                        "data/reorganizertests/test8-expected.asm",
                                                                        "data/reorganizertests/test8-expected.bin")); }

    private boolean test(String inputFile, 
                         String expectedOutputFile,
                         String expectedBinaryOutputFile) throws Exception
    {
        Assert.assertTrue(config.parseArgs(inputFile));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));
        
        // optimize:
        CodeReorganizer ro = new CodeReorganizer(config);
        ro.work(code);
        
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
                System.out.println("Line " + i + " was expected to be:\n" + expectedLine + "\nbut was:\n" + line);
                return false;
            }
        }
        
        // check binary generation (to make sure the reorganizer modified the code correctly):
        BinaryGenerator bg = new BinaryGenerator(config);
        ListOutputStream out = new ListOutputStream();
        bg.writeBytes(code.getMain(), code, out);        
        List<Integer> actualBytes = out.getData();
        
        List<Integer> expectedBytes = new ArrayList<>();        
        InputStream is = Resources.asInputStream(expectedBinaryOutputFile);
        while(is.available() != 0) {
            expectedBytes.add(is.read());
        }
        
        if (actualBytes.size() != expectedBytes.size()) {
            System.out.println("Expected " + expectedBytes.size() + " bytes, but got " + actualBytes.size() + " bytes.");
            return false;
        }

        for(int i = 0;i<actualBytes.size();i++) {
            if (!actualBytes.get(i).equals(expectedBytes.get(i))) {
                System.out.println("Byte " + i + " was expected to be " + expectedBytes.get(i) + ", but was " + actualBytes.get(i));
                return false;
            }
        }
        
        
        return true;
    }    
    
}

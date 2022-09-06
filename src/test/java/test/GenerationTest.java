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
public class GenerationTest {

    private final MDLConfig config;
    private final CodeBase code;

    public GenerationTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/mdl-include.asm",
                                                                          "data/generationtests/mdl-include-expected.asm")); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/mdl-circular.asm",
                                                                          "data/generationtests/mdl-circular-expected.asm")); }
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/mdl-safetylabels.asm",
                                                                          "data/generationtests/mdl-safetylabels-expected.asm")); }
    @Test public void test4() throws IOException { Assert.assertTrue(test("data/generationtests/mdl-safetylabels2.asm",
                                                                          "data/generationtests/mdl-safetylabels2-expected.asm")); }
    @Test public void test5() throws IOException { Assert.assertTrue(test("data/generationtests/wladx-symbols.asm",
                                                                          "data/generationtests/wladx-symbols-expected.asm", "wladx")); }
    @Test public void test6() throws IOException { Assert.assertTrue(test("data/generationtests/wladx-enum.asm",
                                                                          "data/generationtests/wladx-enum-expected.asm", "wladx")); }

    private boolean test(String inputFile, String expectedOutputFile) throws IOException
    {
        return test(inputFile, expectedOutputFile, "mdl");
    }    


    private boolean test(String inputFile, String expectedOutputFile, String dialect) throws IOException
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile, "-safety-labels-for-jumps-to-constants"));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-safety-labels-for-jumps-to-constants",
                                               "-dialect", dialect));
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));

        SourceCodeGenerator scg = new SourceCodeGenerator(config);

        String result = scg.outputFileString(code.outputs.get(0), code);
        if (!compareOutputs(result, expectedOutputFile)) return false;
                
        return true;
    }    
    

    public static boolean compareOutputs(String result, String expectedOutputFile) throws IOException
    {
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
            line = line.trim();
            if (line.length() > 0) {
                expectedLines.add(line);
            }
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
        
        return true;
    }      
    
}

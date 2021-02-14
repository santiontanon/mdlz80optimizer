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
public class SDCCTest {

    private final MDLConfig config;
    private final CodeBase code;

    public SDCCTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/sdcc-base.asm", false,
                                                                          "data/generationtests/sdcc-base-expected.asm")); }
    @Test public void test1dialect() throws IOException { Assert.assertTrue(test("data/generationtests/sdcc-base.asm", true,
                                                                          "data/generationtests/sdcc-base-dialect-expected.asm")); }
    
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/sdcc-ops.asm", false,
                                                                          "data/generationtests/sdcc-ops-expected.asm")); }
    @Test public void test2dialect() throws IOException { Assert.assertTrue(test("data/generationtests/sdcc-ops.asm", true,
                                                                          "data/generationtests/sdcc-ops-dialect-expected.asm")); }
    
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/sdcc-macros.asm", false,
                                                                          "data/generationtests/sdcc-macros-expected.asm")); }
    @Test public void test3dialect() throws IOException { Assert.assertTrue(test("data/generationtests/sdcc-macros.asm", true,
                                                                          "data/generationtests/sdcc-macros-dialect-expected.asm")); }

    private boolean test(String inputFile, boolean mimicTargetDialect, String expectedOutputFile) throws IOException
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-dialect","sdcc"));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        SourceCodeGenerator scg = new SourceCodeGenerator(config);
        scg.mimicTargetDialect = mimicTargetDialect;

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
        
        return true;
    }    
}

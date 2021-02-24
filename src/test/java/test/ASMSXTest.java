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
public class ASMSXTest {

    private final MDLConfig config;
    private final CodeBase code;

    public ASMSXTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-builtin.asm", false, 
                                                                          "data/generationtests/asmsx-builtin-expected.asm", null)); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-builtin2.asm", false, 
                                                                          "data/generationtests/asmsx-builtin2-expected.asm",
                                                                          "data/generationtests/asmsx-builtin2-dialect-expected.asm")); }
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-parenthesis.asm", false,
                                                                          "data/generationtests/asmsx-parenthesis-expected.asm", null)); }
    @Test public void test4() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-parenthesis-zilog.asm", true,
                                                                          "data/generationtests/asmsx-parenthesis-zilog-expected.asm", null)); }
    @Test public void test5() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-crash.asm", true,
                                                                          "data/generationtests/asmsx-crash-expected.asm", null)); }
    @Test public void test6() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-macros.asm", false,
                                                                          "data/generationtests/asmsx-macros-expected.asm", null)); }
    @Test public void test7() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-phase.asm", false,
                                                                          "data/generationtests/asmsx-phase-expected.asm",
                                                                          "data/generationtests/asmsx-phase-dialect-expected.asm")); }
    @Test public void test8() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-megarom.asm", false, 
                                                                          "data/generationtests/asmsx-megarom-expected.asm", null)); }
    

    private boolean test(String inputFile, boolean zilogMode, String expectedOutputFile, String expectedDialectOutputFile) throws IOException
    {
        if (zilogMode) {
            Assert.assertTrue(config.parseArgs(inputFile,"-dialect","asmsx-zilog"));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile,"-dialect","asmsx"));        
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        // Compare standard assembler generation:
        SourceCodeGenerator scg = new SourceCodeGenerator(config);
        String result = scg.sourceFileString(code.getMain(), code);        
        if (!compareOutputs(result, expectedOutputFile)) return false;

        // Compare dialect assembler generation:
        if (expectedDialectOutputFile != null) {
            SourceCodeGenerator scg_dialect = new SourceCodeGenerator(config);
            scg_dialect.mimicTargetDialect = true;
            String resultDialect = scg_dialect.sourceFileString(code.getMain(), code);        
            if (!compareOutputs(resultDialect, expectedDialectOutputFile)) return false;
        }
        
        return true;
    }
    
    
    private boolean compareOutputs(String result, String expectedOutputFile) throws IOException
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

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

    private final MDLConfig mdlConfig;
    private final CodeBase code;

    public ASMSXTest() {
        mdlConfig = new MDLConfig();
        code = new CodeBase(mdlConfig);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-builtin.asm", false, 
                                                                          "data/generationtests/asmsx-builtin-expected.asm")); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-builtin2.asm", false, 
                                                                          "data/generationtests/asmsx-builtin2-expected.asm")); }
    @Test public void test3() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-parenthesis.asm", false,
                                                                          "data/generationtests/asmsx-parenthesis-expected.asm")); }
    @Test public void test4() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-parenthesis-zilog.asm", true,
                                                                          "data/generationtests/asmsx-parenthesis-zilog-expected.asm")); }
    @Test public void test5() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-crash.asm", true,
                                                                          "data/generationtests/asmsx-crash-expected.asm")); }
    @Test public void test6() throws IOException { Assert.assertTrue(test("data/generationtests/asmsx-macros.asm", false,
                                                                          "data/generationtests/asmsx-macros-expected.asm")); }
    

    private boolean test(String inputFile, boolean zilogMode, String expectedOutputFile) throws IOException
    {
        if (zilogMode) {
            Assert.assertTrue(mdlConfig.parseArgs(inputFile,"-dialect","asmsx-zilog"));
        } else {
            Assert.assertTrue(mdlConfig.parseArgs(inputFile,"-dialect","asmsx"));        
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                mdlConfig.codeBaseParser.parseMainSourceFile(mdlConfig.inputFile, code));

        SourceCodeGenerator scg = new SourceCodeGenerator(mdlConfig);

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

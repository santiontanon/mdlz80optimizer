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
import workers.SymbolTableGenerator;

/**
 *
 * @author santi
 */
public class SymbolTableTest {

    private final MDLConfig config;
    private final CodeBase code;

    public SymbolTableTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws IOException { Assert.assertTrue(test("data/generationtests/mdl-circular.asm", null,
                                                                          "data/generationtests/mdl-circular-expected-st.asm")); }
    @Test public void test2() throws IOException { Assert.assertTrue(test("data/generationtests/glass-macro.asm", "glass",
                                                                          "data/generationtests/glass-macro-expected-st.asm")); }

    private boolean test(String inputFile, String dialect, String expectedOutputFile) throws IOException
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile, "-hex#"));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-dialect", dialect, "-hex#"));
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));

        SymbolTableGenerator stg = new SymbolTableGenerator(config);
        stg.includeConstants = true;

        String result = stg.symbolTableString(code);
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

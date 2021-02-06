/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import util.ListOutputStream;
import util.Resources;
import workers.BinaryGenerator;

/**
 *
 * @author santi
 */
public class BinaryGeneratorTest {
        
    private final MDLConfig config;
    private final CodeBase code;

    public BinaryGeneratorTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws Exception { Assert.assertTrue(test("data/generationtests/asmsx-builtin.asm", "asmsx", 
                                                                        "data/generationtests/asmsx-builtin-expected.bin")); }
    @Test public void test2() throws Exception { Assert.assertTrue(test("data/generationtests/asmsx-builtin2.asm", "asmsx", 
                                                                        "data/generationtests/asmsx-builtin2-expected.bin")); }
    @Test public void test3() throws Exception { Assert.assertTrue(test("data/generationtests/asmsx-parenthesis.asm", "asmsx", 
                                                                        "data/generationtests/asmsx-parenthesis-expected.bin")); }
    @Test public void test4() throws Exception { Assert.assertTrue(test("data/generationtests/glass-irp.asm", "glass",
                                                                        "data/generationtests/glass-irp-expected.bin")); }
    @Test public void test5() throws Exception { Assert.assertTrue(test("data/generationtests/sjasm-pletter.asm", "sjasm",
                                                                        "data/generationtests/sjasm-pletter-expected.bin")); }
    @Test public void test6() throws Exception { Assert.assertTrue(test("data/generationtests/ops.asm", null,
                                                                        "data/generationtests/ops-expected.bin")); }
        

    private boolean test(String inputFile, String dialect, String expectedOutputFile) throws Exception
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile,"-dialect",dialect));
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        BinaryGenerator bg = new BinaryGenerator(config);
        ListOutputStream out = new ListOutputStream();
        bg.writeBytes(code.getMain(), code, out);        
        List<Integer> actualBytes = out.getData();
        
        List<Integer> expectedBytes = new ArrayList<>();        
        InputStream is = Resources.asInputStream(expectedOutputFile);
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

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
public class Z180BinaryGeneratorTest {
        
    private final MDLConfig config;
    private final CodeBase code;

    public Z180BinaryGeneratorTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws Exception { Assert.assertTrue(test("data/generationtests/z180-ops.asm", null, 
                                                                        "data/generationtests/z180-ops-expected.bin")); }


    private boolean test(String inputFile, String dialect, String expectedOutputFile) throws Exception
    {
        if (dialect == null) {
            Assert.assertTrue(config.parseArgs(inputFile,"-cpu","z180"));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile,"-cpu","z180","-dialect",dialect));
        }
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFile(config.inputFile, code));

        BinaryGenerator bg = new BinaryGenerator(config);
        ListOutputStream out = new ListOutputStream();
        bg.writeBytes(code.outputs.get(0).main, code, out, 0);        
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

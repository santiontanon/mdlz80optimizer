/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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
import workers.Disassembler;

/**
 *
 * @author santi
 */
public class DisassemblerTest {
    
    private final MDLConfig config;
    private final CodeBase code;
    private final Disassembler da;

    public DisassemblerTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
        da = new Disassembler(config);
        config.registerWorker(da);        
    }
    
    @Test public void test1() throws Exception { Assert.assertTrue(test("data/datests/test1.bin", "data/datests/test1-spec.txt", "z80")); }
    @Test public void test1b() throws Exception { Assert.assertTrue(test("data/datests/test1.bin", "data/datests/test1-spec-b.txt", "z80")); }
    @Test public void test1c() throws Exception { Assert.assertTrue(test("data/datests/test1.bin", "data/datests/test1-spec-c.txt", "z80")); }
    
    
    private boolean test(String inputFile, String specificationFile, String cpu) throws Exception
    {
        if (cpu == null) {
            Assert.assertTrue(config.parseArgs(inputFile, "-da", specificationFile));
        } else {
            Assert.assertTrue(config.parseArgs(inputFile, "-cpu", cpu, "-da", specificationFile));
        }
        Assert.assertTrue(
                "Could not process file " + inputFile,
                da.work(code));
        
        BinaryGenerator bg = new BinaryGenerator(config);
        ListOutputStream out = new ListOutputStream();
        bg.writeBytes(code.outputs.get(0).main, code, out, 0, true);        
        List<Integer> actualBytes = out.getData();
        
        List<Integer> expectedBytes = new ArrayList<>();        
        InputStream is = Resources.asInputStream(inputFile);
        while(is.available() != 0) {
            expectedBytes.add(is.read());
        }
        
        if (actualBytes.size() != expectedBytes.size()) {
            System.out.println("Expected " + expectedBytes.size() + " bytes, but got " + actualBytes.size() + " bytes.");
            return false;
        }

        for(int i = 0;i<actualBytes.size();i++) {
            if (!actualBytes.get(i).equals(expectedBytes.get(i))) {
                System.out.println("Byte " + i + " (#"+config.tokenizer.toHex(i, 4)+") was expected to be " + expectedBytes.get(i) + ", but was " + actualBytes.get(i));
                return false;
            }
        }
        
        return true;
    }

    
}

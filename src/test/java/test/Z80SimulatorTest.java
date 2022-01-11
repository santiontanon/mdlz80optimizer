/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CodeBase;
import java.io.BufferedReader;
import java.util.StringTokenizer;
import org.junit.Assert;
import org.junit.Test;
import util.ListOutputStream;
import util.Resources;
import util.microprocessor.Z80.Z80Core;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.PlainZ80Memory;
import util.microprocessor.Z80.CPUConfig;
import workers.BinaryGenerator;

/**
 *
 * @author santi
 */
public class Z80SimulatorTest {
    private final MDLConfig config;
    private final CodeBase code;

    public Z80SimulatorTest() {
        config = new MDLConfig();
        code = new CodeBase(config);
    }

    @Test public void test1() throws Exception { Assert.assertTrue(test("data/searchtests/instructions.asm", "z80",
                                                                          "data/searchtests/instructions-z80-timing-expected.txt")); }
    @Test public void test2() throws Exception { Assert.assertTrue(test("data/searchtests/instructions.asm", "z80msx",
                                                                          "data/searchtests/instructions-z80msx-timing-expected.txt")); }
    @Test public void test3() throws Exception { Assert.assertTrue(test("data/searchtests/instructions.asm", "z80cpc",
                                                                          "data/searchtests/instructions-z80cpc-timing-expected.txt")); }

    private boolean test(String inputFile, String cpu, String expectedTimings) throws Exception
    {
        Assert.assertTrue(config.parseArgs(inputFile,"-cpu",cpu));
        Assert.assertTrue(
                "Could not parse file " + inputFile,
                config.codeBaseParser.parseMainSourceFiles(config.inputFiles, code));

        // Compile the assembler code to binary:
        BinaryGenerator bg = new BinaryGenerator(config);
        ListOutputStream out = new ListOutputStream();
        bg.writeBytes(code.outputs.get(0).main, code, out, 0, true);
        
        // Setup a Z80 simulator and copy the binary to memory (at address 0 for now):
        PlainZ80Memory z80Memory = new PlainZ80Memory();
        Z80Core z80 = new Z80Core(z80Memory, new PlainZ80IO(), new CPUConfig(config));
        z80.reset();
        int address = 0;
        for(int value: out.getData()) {
            z80Memory.writeByte(address, value);
            address++;
        }
        
        BufferedReader br = Resources.asReader(expectedTimings);
        String line = br.readLine();
        long start = z80.getTStates();
        int step = 0;
        while(line != null) {
            StringTokenizer st = new StringTokenizer(line, " \t");
            int expectedTime = Integer.parseInt(st.nextToken());
            z80.executeOneInstruction();
            long end = z80.getTStates();
            long time = end-start;
            if (time != expectedTime) {
                config.error("Expected time " + expectedTime + " but was " + time + " at step " + step);
                return false;
            }
            start = end;
            line = br.readLine();
            step++;
        }
        
        return true;
    }        
}

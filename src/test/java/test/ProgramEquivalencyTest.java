/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.CodeStatement;
import code.SourceFile;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import parser.SourceLine;
import util.Resources;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.PlainZ80Memory;
import util.microprocessor.ProcessorException;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class ProgramEquivalencyTest {
    private final MDLConfig config;
    private final Random r;
    private final List<Integer> HNflags;
    private final List<Integer> HNPVFlags;
    
    private final int REPETITIONS = 5000;

    public ProgramEquivalencyTest() {
        config = new MDLConfig();
        r = new Random();     
        HNflags = new ArrayList<>();
        HNflags.add(CPUConstants.flag_H);
        HNflags.add(CPUConstants.flag_N);
        HNPVFlags = new ArrayList<>();
        HNPVFlags.add(CPUConstants.flag_H);
        HNPVFlags.add(CPUConstants.flag_N);
        HNPVFlags.add(CPUConstants.flag_PV);
    }

    @Test public void test1() throws Exception { test("and a", "and a", false, HNflags); }
    @Test public void test2() throws Exception { test("and a", "or a", false, HNflags); }
    @Test public void test3() throws Exception { test("sub a\nadd a,h", "ld a,h\nor a", false, HNPVFlags); }
    @Test public void test4() throws Exception { test("or a\nadd a,a", "or a\nadc a,a", false, HNflags); }
    @Test public void test5() throws Exception { test("sub a\nsub h", "sub a\nsbc h", false, HNflags); }
    @Test public void test6() throws Exception { test("xor a\nsrl a", "xor a\nsra a", false, HNflags); }
    @Test public void test6a() throws Exception { test("xor a\nsrl a", "xor a", false, HNflags); }

    // RLCA/RLA/RRCA/RRA/RLC/RL/RRC/RR
    @Test public void test7() throws Exception { test("xor a", "xor a\nrrc a", false, HNflags); }
    @Test public void test7a() throws Exception { test("xor a", "xor a\nrrca", false, HNflags); }
    @Test public void test8() throws Exception { test("xor a\nrla", "xor a\nrlc a", false, HNflags); }
    @Test public void test8a() throws Exception { test("xor a\nrl a", "xor a\nrlc a", false, HNflags); }

    @Test public void test9() throws Exception { test("add a, a", "sla a", false, HNPVFlags); }

    @Test public void test10() throws Exception { test("add a, a\nsbc a, a", "adc a, a\nsbc a, a", false, HNPVFlags); }
    @Test public void test11() throws Exception { test("or h", "or h\ncp 0", false, HNPVFlags); }
    
    // Some of these are slow, so, just run them every once in a while:
//    @Test public void sbotest1() throws Exception { testSequencesFromFile("data/equivalencies-l1.txt"); }
//    @Test public void sbotest2() throws Exception { testSequencesFromFile("data/equivalencies-l2-to-l1.txt"); }
//    @Test public void sbotest3() throws Exception { testSequencesFromFile("data/equivalencies-l2.txt"); }
    
    
    private void test(String program1, String program2, boolean checkMemory, List<Integer> flagsToIgnore) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy"));
 
        CodeBase code = new CodeBase(config);
        List<CodeStatement> statements1 = parseSequenceString(program1, "\n", code);        
        List<CodeStatement> statements2 = parseSequenceString(program2, "\n", code);
        
        Assert.assertTrue(comparePrograms(statements1, statements2,
                                          flagsToIgnore,
                                          code, checkMemory, REPETITIONS));
    }
    
    
    private void testSequencesFromFile(String sequencesFile) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy"));
        CodeBase code = new CodeBase(config);
        
        BufferedReader br = Resources.asReader(sequencesFile);
        while(true) {
            String line = br.readLine();
            if (line == null) break;
            String columns[] = line.split("\t");
            String seq1 = columns[0];
            String seq2 = columns[1];
            String flagStr = (columns.length >= 3 ? columns[2]:"");
            List<Integer> flagsToIgnore = new ArrayList<>();
            for(String flag:flagStr.split(",")) {
                if (!flag.trim().isEmpty()) {
                    flagsToIgnore.add(CPUConstants.flagByName(flag));
                }
            }
            
            // parse sequences:
            List<CodeStatement> statements1 = parseSequenceString(seq1, ";", code);
            List<CodeStatement> statements2 = parseSequenceString(seq2, ";", code);
            
            Assert.assertTrue(comparePrograms(statements1, statements2,
                                              flagsToIgnore,
                                              code, false, REPETITIONS));
        }        
    }
    
    
    private List<CodeStatement> parseSequenceString(String sequence, String separator, CodeBase code) throws Exception
    {
        String lines[] = sequence.split(separator);
 
        SourceFile f = new SourceFile("dummy1", null, null, code, config);
        List<CodeStatement> statements = new ArrayList<>();
                
        for(int i = 0;i<lines.length;i++) {
            String line = lines[i];
            List<String> tokens = config.tokenizer.tokenize(line);
            List<CodeStatement> l = config.lineParser.parse(tokens, new SourceLine(line, f, i), f, null, code, config);
            statements.addAll(l);
        }
        return statements;
    }
    
    
    private boolean comparePrograms(List<CodeStatement> statements1, 
                                    List<CodeStatement> statements2,
                                    List<Integer> flagsToIgnore,
                                    CodeBase code, 
                                    boolean checkMemory, int repetitions) throws Exception
    {
        PlainZ80Memory z80Memory1 = new PlainZ80Memory();
        Z80Core z801 = new Z80Core(z80Memory1, new PlainZ80IO(), new CPUConfig(config));
        PlainZ80Memory z80Memory2 = new PlainZ80Memory();
        Z80Core z802 = new Z80Core(z80Memory2, new PlainZ80IO(), new CPUConfig(config));
        
        for(int i = 0;i<repetitions;i++) {
            if (!evaluatePrograms(statements1, statements2, 
                    flagsToIgnore,
                    code, 
                    z80Memory1, z801, z80Memory2, z802,
                    checkMemory)) return false;
        }        
        return true;
    }
    
    
    private boolean evaluatePrograms(List<CodeStatement> statements1, 
                                     List<CodeStatement> statements2,
                                     List<Integer> flagsToIgnore,
                                     CodeBase code,
                                     PlainZ80Memory z80Memory1, Z80Core z801,
                                     PlainZ80Memory z80Memory2, Z80Core z802,
                                     boolean checkMemory) throws Exception
    {
        // randomize the start address from a range:
        int minStartAddress = 0x0000;
        int maxStartAddress = 0x0f00;
        int maxStackSize = 0x0100;
        
        int startAddress = minStartAddress + r.nextInt(maxStartAddress - minStartAddress);
                
        // Assign random values to all the input parameters:
        /*
        for(String parameter:parameters) {            
            // give it a random value:
            int v = r.nextInt(0xf000)+0x1000;   // prevent constants from having the same range as where the code is, just in case
            match.addVariableMatch(parameter, Expression.constantExpression(v, config));            
//            System.out.println("parameter " + parameter + " = " + v);
        }
        */
        
        if (checkMemory) {
            for(int i = 0;i<0x10000;i++) {
                int v = r.nextInt(256);
                z80Memory1.writeByte(i, v);
                z80Memory2.writeByte(i, v);
            }
        }
        
        // Instantiate the Z80 simulator and run the simulations:
        // Reset the CPU:
        z801.reset();
        z802.reset();
        List<Integer> initialValues = new ArrayList<>();
        for(CPUConstants.RegisterNames register: CPUConstants.eightBitRegisters) {
            int v = r.nextInt(256);
            z801.setRegisterValue(register, v);
            z802.setRegisterValue(register, v);
            initialValues.add(v);
        }
        z801.setProgramCounter(startAddress);
        z802.setProgramCounter(startAddress);
        
        // Simulate the pattern and replacement:
        int patternLastAddress = simulateProgram(statements1, startAddress, z801, z80Memory1, code);
        int replacementLastAddress = simulateProgram(statements2, startAddress, z802, z80Memory2, code);
                
        // Compare the output state of the simulator:
        boolean differences = false;
        for(CPUConstants.RegisterNames register: CPUConstants.eightBitRegisters) {
            int v1 = z801.getRegisterValue(register);
            int v2 = z802.getRegisterValue(register);
            if (register == CPUConstants.RegisterNames.F || register == CPUConstants.RegisterNames.F_ALT) {
                // Reset flags 3 and 5 (unused):
                v1 = v1 & (CPUConstants.flag_3_N & CPUConstants.flag_5_N);
                v2 = v2 & (CPUConstants.flag_3_N & CPUConstants.flag_5_N);
                
                // Ignore the flags that we know are not important:
                for(int flag:flagsToIgnore) {
                    v1 = v1 | flag;
                    v2 = v2 | flag;
                }                
            }
            if (v1 != v2) {
                if (register == CPUConstants.RegisterNames.F || register == CPUConstants.RegisterNames.F_ALT) {
                    for(int flag = 0;flag<8;flag++) {
                        if ((v1 & CPUConstants.flags[flag]) != (v2 & CPUConstants.flags[flag])) {
                            System.out.println("Difference in flag: " + CPUConstants.flagNames[flag]);
                            differences = true;
                        }
                    }
                }
                System.out.println("Simulations differ in register " + CPUConstants.registerName(register) + ": " + 
                        config.tokenizer.toHex(z801.getRegisterValue(register), 2) + " != " +
                        config.tokenizer.toHex(z802.getRegisterValue(register), 2));
                differences = true;
            }
        }
        
        if (checkMemory) {
            for(int i = 0;i<0x10000;i++) {
                if (i>=startAddress && (i<patternLastAddress || i<replacementLastAddress)) continue;
                if (i>0x10000 - maxStackSize) continue;
                if (z80Memory1.readByte(i) != z80Memory2.readByte(i)) {
                    System.out.println("Simulations differ in memory address " + config.tokenizer.toHex(i, 4));
                    differences = true;
                }
            }
        }
        
        if (differences) {
            showInstantiatedProgram("program 1:", statements1);
            showInstantiatedProgram("program 2:", statements2);
            System.out.println("program 1 addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(patternLastAddress, 4));
            System.out.println("program 2 addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(replacementLastAddress, 4));
            System.out.println("Initial register values:");
            for(int i = 0;i<CPUConstants.eightBitRegisters.length;i++) {
                CPUConstants.RegisterNames register = CPUConstants.eightBitRegisters[i];
                System.out.println("    " + CPUConstants.registerName(register) + " = " + initialValues.get(i));
            }
            return false;
        }
                
        return true;
    }
    
    
    private void showInstantiatedProgram(String name, List<CodeStatement> l) {
        System.out.println(name);
        for(CodeStatement s:l) {
            System.out.println("    " + s);
        }
    }
    
    
    private int simulateProgram(List<CodeStatement> l,
                                int startAddress,
                                Z80Core z80,
                                PlainZ80Memory memory,
                                CodeBase code) throws ProcessorException {
        List<Integer> opAddresses = new ArrayList<>();
        int currentAddress = startAddress;
//        System.out.println("--------");
        for(CodeStatement s:l) {
            if (s.op == null) continue;
            CPUOp op = s.op;
//                System.out.println("    " + op);
            Assert.assertNotNull(op);
//                System.out.println(config.tokenizer.toHex(startAddress, 4) + ":  " + op);

            List<Integer> bytes = op.assembleToBytes(null, code, config);
            Assert.assertNotNull(bytes);
            Assert.assertFalse(bytes.isEmpty());

            opAddresses.add(currentAddress);
            for(Integer value:bytes) {
                memory.writeByte(currentAddress, value);
                currentAddress++;
            }
        }
                
        // Simulate the program:
        memory.writeProtect(startAddress, currentAddress);
        int steps = 0;
        while(opAddresses.contains(z80.getProgramCounter())) {
            z80.executeOneInstruction();
            steps++;
        }
        memory.clearWriteProtections();
            
        return currentAddress;
    }
}

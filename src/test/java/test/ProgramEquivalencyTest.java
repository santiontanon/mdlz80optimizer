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
import code.Expression;
import code.SourceFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import parser.SourceLine;
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
    private final List<Integer> halfFlag;

    public ProgramEquivalencyTest() {
        config = new MDLConfig();
        r = new Random();     
        halfFlag = new ArrayList<>();
        halfFlag.add(CPUConstants.flag_H);
    }

    @Test public void test1() throws Exception { test("and a", "and a", false, halfFlag); }
    @Test public void test2() throws Exception { test("and a", "or a", false, halfFlag); }


    private void test(String program1, String program2, boolean checkMemory, List<Integer> flagsToIgnore) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy"));
 
        String lines1[] = program1.split("\n");
        String lines2[] = program2.split("\n");
 
        CodeBase code = new CodeBase(config);
        SourceFile f1 = new SourceFile("dummy1", null, null, code, config);
        List<CodeStatement> statements1 = new ArrayList<>();
        
        SourceFile f2 = new SourceFile("dummy2", null, null, code, config);
        List<CodeStatement> statements2 = new ArrayList<>();
        
        for(int i = 0;i<lines1.length;i++) {
            String line = lines1[i];
            List<String> tokens = config.tokenizer.tokenize(line);
            List<CodeStatement> l = config.lineParser.parse(tokens, new SourceLine(line, f1, i), f1, null, code, config);
            statements1.addAll(l);
        }

        for(int i = 0;i<lines2.length;i++) {
            String line = lines2[i];
            List<String> tokens = config.tokenizer.tokenize(line);
            List<CodeStatement> l = config.lineParser.parse(tokens, new SourceLine(line, f2, i), f2, null, code, config);
            statements2.addAll(l);
        }

        Assert.assertTrue(comparePrograms(statements1, statements2,
                                          flagsToIgnore,
                                          code, checkMemory, 1000));
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
        for(CPUConstants.RegisterNames register: CPUConstants.eightBitRegisters) {
            int v = r.nextInt(256);
            z801.setRegisterValue(register, v);
            z802.setRegisterValue(register, v);
        }
        z801.setProgramCounter(startAddress);
        z802.setProgramCounter(startAddress);
        
        // Simulate the pattern and replacement:
        int patternLastAddress = simulateProgram(statements1, startAddress, z801, z80Memory1, code);
        int replacementLastAddress = simulateProgram(statements2, startAddress, z802, z80Memory2, code);
                
        // Compare the output state of the simulator:
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
                        }
                    }
                }
                System.out.println("Simulations differ in register " + CPUConstants.registerName(register) + ": " + 
                        config.tokenizer.toHex(z801.getRegisterValue(register), 2) + " != " +
                        config.tokenizer.toHex(z802.getRegisterValue(register), 2));
                System.out.println("program 1 addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(patternLastAddress, 4));
                System.out.println("program 2 addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(replacementLastAddress, 4));
                showInstantiatedProgram("program 1:", statements1);
                showInstantiatedProgram("program 2:", statements2);
                return false;
            }
        }
        
        if (checkMemory) {
            for(int i = 0;i<0x10000;i++) {
                if (i>=startAddress && (i<patternLastAddress || i<replacementLastAddress)) continue;
                if (i>0x10000 - maxStackSize) continue;
                if (z80Memory1.readByte(i) != z80Memory2.readByte(i)) {
                    System.out.println("Simulations differ in memory address " + config.tokenizer.toHex(i, 4));
                    System.out.println("Pattern addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(patternLastAddress, 4));
                    System.out.println("Replacement addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(replacementLastAddress, 4));
                    showInstantiatedProgram("program 1:", statements1);
                    showInstantiatedProgram("program 2:", statements2);
                    return false;
                }
            }
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
        
//        System.out.println("Executed " + steps+ " steps, taking a total time of " + z80.getTStates());
    
        return currentAddress;
    }
}

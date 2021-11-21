/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.tuple.Pair;
import util.microprocessor.IMemory;
import util.microprocessor.TrackingZ80Memory;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class PrecomputedTestCaseGeneratorForOptimization implements PrecomputedTestCaseGenerator {

    Random random = new Random();
    Specification spec;
    List<CodeStatement> codeToOptimize;
    List<RegisterNames> inputRegisters;
    List<String> allowedRegisters;
    List<RegisterNames> goalRegisters;
    List<Integer> goalFlags;
    int startAddress;
    Z80Core z80;
    IMemory memory;
    CodeBase code;
    
    List<RegisterNames> appearInOr, appearInXor, appearInAnd, appearInAddSub,
                        appearInAdc, appearInSbc;
    
    // Precalculated things that are constant accross tests:
    List<CPUConstants.RegisterNames> registersToInit = new ArrayList<>();
    CPUConstants.RegisterNames startRegisters[];
    Integer startRegisterValues[];
    List<Integer> opAddresses;
    List<Integer> opBytes;
    
    public PrecomputedTestCaseGeneratorForOptimization(
            Specification a_spec,
            List<CodeStatement> a_codeToOptimize,
            List<RegisterNames> a_inputRegisters,
            List<RegisterNames> a_goalRegisters,
            List<Integer> a_goalFlags,
            Z80Core a_z80,
            IMemory a_memory,
            CodeBase a_code,
            MDLConfig config) {
        spec = a_spec;
        codeToOptimize = a_codeToOptimize;
        inputRegisters = a_inputRegisters;
        allowedRegisters = spec.allowedRegisters;
        goalRegisters = a_goalRegisters;
        goalFlags = a_goalFlags;
        startAddress = spec.codeStartAddress;
        z80 = a_z80;
        memory = a_memory;
        code = a_code;
        
        appearInOr = new ArrayList<>();
        appearInXor = new ArrayList<>();
        appearInAnd = new ArrayList<>();
        appearInAddSub = new ArrayList<>();
        appearInAdc = new ArrayList<>();
        appearInSbc = new ArrayList<>();
        for(CodeStatement s:codeToOptimize) {
            if (s.op == null) continue;
            for(int i = 0;i<s.op.args.size();i++) {
                Expression arg = s.op.args.get(i);
                if (arg.type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                    CodeBase.isRegister(arg.registerOrFlagName)) {
                    RegisterNames reg = CPUConstants.registerByName(arg.registerOrFlagName);
                    // For now, consider only the last register:
                    if (reg != null && i == s.op.args.size()-1) {
                        if (s.op.spec.getName().equals("or")) {
                            appearInOr.add(reg);
                        } else if (s.op.spec.getName().equals("xor")) {
                            appearInXor.add(reg);
                        } else if (s.op.spec.getName().equals("and")) {
                            appearInAnd.add(reg);
                        } else if (s.op.spec.getName().equals("add") ||
                                   s.op.spec.getName().equals("sub")) {
                            appearInAddSub.add(reg);
                        } else if (s.op.spec.getName().equals("adc")) {
                            appearInAdc.add(reg);
                        } else if (s.op.spec.getName().equals("sbc")) {
                            appearInSbc.add(reg);
                        }
                    }
                }
            }
        }
        if (!appearInOr.isEmpty()) appearInOr.add(RegisterNames.A);
        if (!appearInXor.isEmpty()) appearInXor.add(RegisterNames.A);
        if (!appearInAnd.isEmpty()) appearInAnd.add(RegisterNames.A);
        if (!appearInAddSub.isEmpty()) appearInAddSub.add(RegisterNames.A);        
        
        // Assign random values to the input registers:
        registersToInit.addAll(inputRegisters);
        for(CPUConstants.RegisterNames reg:goalRegisters) {
            if (CPUConstants.is8bitRegister(reg) && !registersToInit.contains(reg)) {
                registersToInit.add(reg);
            }
        }
        for(String regName:allowedRegisters) {
            CPUConstants.RegisterNames reg = CPUConstants.registerByName(regName);
            if (CPUConstants.is8bitRegister(reg) && !registersToInit.contains(reg)) {
                registersToInit.add(reg);
            }
        }
        startRegisters = new CPUConstants.RegisterNames[registersToInit.size()];
        startRegisterValues = new Integer[registersToInit.size()];
        for(int i = 0;i<registersToInit.size();i++) {
            startRegisters[i] = registersToInit.get(i);
            startRegisterValues[i] = null;
            for(SpecificationExpression exp:spec.startState) {
                if (exp.leftRegister == startRegisters[i]) {
                    startRegisterValues[i] = exp.right.evaluateToInteger(null, code, true);
                }
            }
        }
        
        opAddresses = new ArrayList<>();
        opBytes = new ArrayList<>();
        int currentAddress = startAddress;
        for(CodeStatement s:codeToOptimize) {
            if (s.op == null) continue;
            CPUOp op = s.op;
            List<Integer> bytes = op.assembleToBytes(null, code, config);
            if (bytes == null) {
                config.error("Error generating bytes to initialize the search-based optimizer, for instruction: " + op);
            } else {
                opBytes.addAll(bytes);
                opAddresses.add(currentAddress);
                currentAddress += bytes.size();
            }
        }

//        System.out.println("registersToInit: " + registersToInit);
//        System.out.println("appearInSbc: " + appearInSbc);        
//        System.out.println("inputRegisters: " + inputRegisters);        
//        System.out.println("allowedRegisters: " + allowedRegisters);        
//        System.out.println("goalRegisters: " + goalRegisters);        
//        System.out.println("goalFlags: " + goalFlags);        
//        System.out.println("allowRamUse: " + spec.allowRamUse);        
    }
    
    
    @Override
    public PrecomputedTestCase generateTestCase(MDLConfig config) {
        PrecomputedTestCase test = new PrecomputedTestCase();        
        if (spec.allowRamUse) test.trackMemoryWrites = true;

        test.startRegisters = startRegisters;
        test.startRegisterValues = new int[registersToInit.size()];
        for(int i = 0;i<registersToInit.size();i++) {
            if (startRegisterValues[i] != null) {
                test.startRegisterValues[i] = startRegisterValues[i];
            } else {
                // 0 is a special value that often elicits special cases, so
                // give it a higher probability:
                if (random.nextDouble() < 0.05) {
                    test.startRegisterValues[i] = 0;
                } else {
                    if ((appearInOr.contains(test.startRegisters[i]) ||
                         appearInXor.contains(test.startRegisters[i]) ||
                         appearInAddSub.contains(test.startRegisters[i])) &&
                        random.nextDouble() < 0.1) {
                        test.startRegisterValues[i] = 0;
                    } else if (appearInAnd.contains(test.startRegisters[i]) &&
                               random.nextDouble() < 0.1) {
                        test.startRegisterValues[i] = 0xff;
                    } else {
                        test.startRegisterValues[i] = random.nextInt(256);                
                    }
                }
            }
        }
        for(RegisterNames reg:appearInAdc) {
            if (CPUConstants.is8bitRegister(reg)) {
                int AIdx = registersToInit.indexOf(RegisterNames.A);
                if (AIdx >= 0 && random.nextDouble() < 0.1) {
                    int regIdx = registersToInit.indexOf(reg);
                    if (regIdx >= 0) {
                        test.startRegisterValues[regIdx] = 255 - test.startRegisterValues[AIdx];
                    }
                }
            } else {
                int HIdx = registersToInit.indexOf(RegisterNames.H);
                int LIdx = registersToInit.indexOf(RegisterNames.L);
                if (HIdx >= 0 && LIdx >=0 && random.nextDouble() < 0.1) {
                    RegisterNames pair[] = CPUConstants.primitive8BitRegistersOf(reg);
                    int regHIdx = registersToInit.indexOf(pair[0]);
                    int regLIdx = registersToInit.indexOf(pair[1]);
                    if (regHIdx >= 0 && regLIdx >=0) {
                        int HLValue = test.startRegisterValues[HIdx]*256 + test.startRegisterValues[LIdx];
                        int regValue = 65535 - HLValue;
                        test.startRegisterValues[regHIdx] = regValue/256;
                        test.startRegisterValues[regLIdx] = regValue%256;
                    }
                }
            }
        }
        for(RegisterNames reg:appearInSbc) {
            if (CPUConstants.is8bitRegister(reg)) {
                int AIdx = registersToInit.indexOf(RegisterNames.A);
                if (AIdx >= 0 && random.nextDouble() < 0.1) {
                    int regIdx = registersToInit.indexOf(reg);
                    if (regIdx >= 0) {
                        test.startRegisterValues[regIdx] = test.startRegisterValues[AIdx];
                    }
                }
            } else {
                int HIdx = registersToInit.indexOf(RegisterNames.H);
                int LIdx = registersToInit.indexOf(RegisterNames.L);
                if (HIdx >= 0 && LIdx >=0 && random.nextDouble() < 0.1) {
                    RegisterNames pair[] = CPUConstants.primitive8BitRegistersOf(reg);
                    int regHIdx = registersToInit.indexOf(pair[0]);
                    int regLIdx = registersToInit.indexOf(pair[1]);
                    if (regHIdx >= 0 && regLIdx >=0) {
                        test.startRegisterValues[regHIdx] = test.startRegisterValues[HIdx];
                        test.startRegisterValues[regLIdx] = test.startRegisterValues[LIdx];
                    }
                }
            }
        }

        // Set up the simulator:
        int currentAddress = startAddress;
        for(Integer value:opBytes) {
            memory.writeByte(currentAddress, value);
            currentAddress++;
        }
        z80.resetTStates();
        test.initCPU(z80);
        z80.setProgramCounter(startAddress);
        
        if (test.trackMemoryWrites) {
            ((TrackingZ80Memory)memory).clearMemoryAccesses();
        }
                
        // Simulate the program:
        memory.writeProtect(startAddress, currentAddress);
        int steps = 0;
        try {
            while(opAddresses.contains(z80.getProgramCounter())) {
                z80.executeOneInstruction();
                steps++;
            }
        } catch(Exception e) {
            config.error("Could not generate test case for search-based optimizer!");
            config.error("Exception: " + e.getMessage());
            config.error(Arrays.toString(e.getStackTrace()));
            return null;
        }
        memory.clearWriteProtections();
        
        // Set the goal register/flag values (consider only the 8bit ones):
        List<CPUConstants.RegisterNames> goalRegisters8bit = new ArrayList<>();
        for(CPUConstants.RegisterNames reg:goalRegisters) {
            if (CPUConstants.is8bitRegister(reg)) goalRegisters8bit.add(reg);
        }
        test.goalRegisters = new CPUConstants.RegisterNames[goalRegisters8bit.size()];
        test.goalRegisterValues = new int[goalRegisters8bit.size()];
        for(int i = 0;i<goalRegisters8bit.size();i++) {
            test.goalRegisters[i] = goalRegisters8bit.get(i);
            test.goalRegisterValues[i] = z80.getRegisterValue(goalRegisters8bit.get(i));
        }
        test.goalFlags = new int[goalFlags.size()];
        test.goalFlagValues = new boolean[goalFlags.size()];
        for(int i = 0;i<goalFlags.size();i++) {
            test.goalFlags[i] = goalFlags.get(i);
            test.goalFlagValues[i] = z80.getFlagValue(CPUConstants.flagIndex(goalFlags.get(i)));
        }
        
        if (spec.allowRamUse) {
            // Store the initial values of the RAM positions that were read:
            List<Integer> addressesToInit = new ArrayList<>();
            List<Integer> valuesToInitTo = new ArrayList<>();
            for(Pair<Integer,Integer> read:((TrackingZ80Memory)memory).getMemoryReads()) {
                if (!addressesToInit.contains(read.getLeft()) &&
                     read.getLeft()<startAddress || 
                     read.getLeft()>=currentAddress) {
                    
                    addressesToInit.add(read.getLeft());
                    valuesToInitTo.add(read.getRight());
//                    System.out.println("  read " + read.getRight() + " from " + read.getLeft());
                }                
            }
            if (test.startMemoryAddresses == null) {
                test.startMemoryAddresses = new int[addressesToInit.size()];
                test.startMemoryValues = new int[valuesToInitTo.size()];
                for(int i = 0;i<addressesToInit.size();i++) {
                    test.startMemoryAddresses[i] = addressesToInit.get(i);
                    test.startMemoryValues[i] = valuesToInitTo.get(i);
                }
            } else {
                int newAddresses[] = new int[test.startMemoryAddresses.length+addressesToInit.size()];
                int newValues[] = new int[test.startMemoryValues.length+valuesToInitTo.size()];
                for(int i = 0;i<newAddresses.length;i++) {
                    if (i<test.startMemoryAddresses.length) {
                        newAddresses[i] = test.startMemoryAddresses[i];
                        newValues[i] = test.startMemoryValues[i];
                    } else {
                        newAddresses[i] = addressesToInit.get(i - test.startMemoryAddresses.length);
                        newValues[i] = valuesToInitTo.get(i - test.startMemoryAddresses.length);
                    }
                }
                test.startMemoryAddresses = newAddresses;
                test.startMemoryValues = newValues;
            }
            
            
            List<Integer> goalAddresses = new ArrayList<>();
            for(Integer address:((TrackingZ80Memory)memory).getMemoryWrites()) {
                if (!goalAddresses.contains(address)) goalAddresses.add(address);
            }
            test.goalMemoryAddresses = new int[goalAddresses.size()];
            test.goalMemoryValues = new int[goalAddresses.size()];
            for(int i = 0;i<goalAddresses.size();i++) {
                test.goalMemoryAddresses[i] = goalAddresses.get(i);
                test.goalMemoryValues[i] = z80.readByte(goalAddresses.get(i));
            }
            
            // Randomize those positions for the next test, in case the z80
            // memory is reused to create the next test:
            for(int address:addressesToInit) {
                memory.writeByte(address, random.nextInt(256));
//                System.out.println("    random init of " + address);
            }            
        }        
                            
        return test;
    }    
}

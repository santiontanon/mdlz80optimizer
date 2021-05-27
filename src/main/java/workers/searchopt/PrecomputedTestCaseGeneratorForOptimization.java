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
import util.microprocessor.PlainZ80Memory;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class PrecomputedTestCaseGeneratorForOptimization implements PrecomputedTestCaseGenerator {

    Random random = new Random();
    List<CodeStatement> codeToOptimize;
    List<RegisterNames> inputRegisters;
    List<String> allowedRegisters;
    List<RegisterNames> goalRegisters;
    List<Integer> goalFlags;
    int startAddress;
    Z80Core z80;
    PlainZ80Memory memory;
    CodeBase code;
    
    List<RegisterNames> appearInOr, appearInXor, appearInAnd, appearInAddSub;
    
    public PrecomputedTestCaseGeneratorForOptimization(
            List<CodeStatement> a_codeToOptimize,
            List<RegisterNames> a_inputRegisters,
            List<String> a_allowedRegisters,
            List<RegisterNames> a_goalRegisters,
            List<Integer> a_goalFlags,
            int a_startAddress,
            Z80Core a_z80,
            PlainZ80Memory a_memory,
            CodeBase a_code) {
        codeToOptimize = a_codeToOptimize;
        inputRegisters = a_inputRegisters;
        allowedRegisters = a_allowedRegisters;
        goalRegisters = a_goalRegisters;
        goalFlags = a_goalFlags;
        startAddress = a_startAddress;
        z80 = a_z80;
        memory = a_memory;
        code = a_code;
        
        appearInOr = new ArrayList<>();
        appearInXor = new ArrayList<>();
        appearInAnd = new ArrayList<>();
        appearInAddSub = new ArrayList<>();
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
                                   s.op.spec.getName().equals("adc") ||
                                   s.op.spec.getName().equals("sub") ||
                                   s.op.spec.getName().equals("sbc")) {
                            appearInAddSub.add(reg);
                        }
                    }
                }
            }
        }
        if (!appearInOr.isEmpty()) appearInOr.add(RegisterNames.A);
        if (!appearInXor.isEmpty()) appearInXor.add(RegisterNames.A);
        if (!appearInAnd.isEmpty()) appearInAnd.add(RegisterNames.A);
        if (!appearInAddSub.isEmpty()) appearInAddSub.add(RegisterNames.A);
    }
    
    @Override
    public PrecomputedTestCase generateTestCase(MDLConfig config) {
        PrecomputedTestCase test = new PrecomputedTestCase();
        
        // Assign random values to the input registers:
        List<CPUConstants.RegisterNames> registersToInit = new ArrayList<>();
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
        test.startRegisters = new CPUConstants.RegisterNames[registersToInit.size()];
        test.startRegisterValues = new int[registersToInit.size()];
        for(int i = 0;i<registersToInit.size();i++) {
            test.startRegisters[i] = registersToInit.get(i);
            test.startRegisterValues[i] = random.nextInt(256);
            if (appearInOr.contains(test.startRegisters[i]) ||
                appearInXor.contains(test.startRegisters[i]) ||
                appearInAddSub.contains(test.startRegisters[i])) {
                if (random.nextDouble() < 0.1) {
                    test.startRegisterValues[i] = 0;
                }
            } 
            if (appearInAnd.contains(test.startRegisters[i])) {
                if (random.nextDouble() < 0.1) {
                    test.startRegisterValues[i] = 0xff;
                }
            } 
        }
        
        // Set up the simulator:
        List<Integer> opAddresses = new ArrayList<>();
        int currentAddress = startAddress;
        for(CodeStatement s:codeToOptimize) {
            if (s.op == null) continue;
            CPUOp op = s.op;
            List<Integer> bytes = op.assembleToBytes(null, code, config);
            opAddresses.add(currentAddress);
            for(Integer value:bytes) {
                memory.writeByte(currentAddress, value);
                currentAddress++;
            }
        }
        z80.resetTStates();
        test.initCPU(z80);
        z80.setProgramCounter(startAddress);
                
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
                    
        return test;
    }    
}

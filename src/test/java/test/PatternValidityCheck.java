/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.ProcessorException;
import util.microprocessor.TrackingZ80Memory;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.Z80Core;
import workers.pattopt.CPUOpPattern;
import workers.pattopt.Pattern;
import workers.pattopt.PatternBasedOptimizer;
import workers.pattopt.PatternMatch;

/**
 *
 * @author santi
 */
public class PatternValidityCheck {
    private final MDLConfig config;
    private final PatternBasedOptimizer pbo;
    private final Random r;

    public PatternValidityCheck() {
        config = new MDLConfig();
        pbo = new PatternBasedOptimizer(config);
        config.registerWorker(pbo);
        r = new Random();        
    }

    // Individual pattern tests (not needed as "testAll" already tests them all!):
//    @Test public void test2() throws Exception { test("data/pbo-patterns.txt", "cp02ora", false); }
//    @Test public void test3() throws Exception { test("data/pbo-patterns.txt", "cp12deca", false); }
//
//    @Test public void test100() throws Exception { test("data/pbo-patterns.txt", "sdcc16bitadd", true); }
//    @Test public void test101() throws Exception { test("data/pbo-patterns.txt", "sdcc16bitcp", true); }
//    @Test public void test102() throws Exception { test("data/pbo-patterns.txt", "move-to-top-of-stack", true); }
//    @Test public void test103() throws Exception { test("data/pbo-patterns.txt", "unnecessary-ld-to-reg", true); }
//    
//    @Test public void testSpeed1() throws Exception { test("data/pbo-patterns-speed.txt", "push2ld", false); }

    // Test all patterns without wildcards/repetitions:
//    @Test public void testAllNoMemory() throws Exception { testAll("data/pbo-patterns.txt", false); }
    @Test public void testAll() throws Exception { testAll("data/pbo-patterns.txt", true); }
    
    
    private void test(String patternsFile, String patternName, boolean checkMemory) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy", "-popatterns", patternsFile));
        
        pbo.initPatterns();
        Pattern pattern = pbo.getPattern(patternName);
        Assert.assertNotNull(pattern);

        // Construct the code snippets:
        CodeBase code = new CodeBase(config);
        List<Pattern> instantiated = allPatternInstantiations(pattern, code);
        Assert.assertNotNull(instantiated);
        System.out.println("instantiated ("+patternName+"): " + instantiated.size());
        for(Pattern p:instantiated) {
            if (!p.fullyInstantiated()) {
                continue;
            }
            config.info("Instantiated pattern:\n" + p);
            // Extract the parameters:
            List<String> parameters = getParameters(p);
            Assert.assertNotNull("parameters is null", parameters);
            List<Integer> flagsToIgnore = getFlagsToIgnore(p);
            Assert.assertNotNull("flagsToIgnore is null", flagsToIgnore);
//            System.out.println("flagsToIgnore: " + flagsToIgnore);
            List<CPUConstants.RegisterNames> registersToIgnore = getRegistersToIgnore(p);
            Assert.assertNotNull("registersToIgnore is null", registersToIgnore);
//            System.out.println("registersToIgnore: " + registersToIgnore);

            Assert.assertTrue(evaluatePattern(p, parameters, 
                                              flagsToIgnore, registersToIgnore,
                                              code, checkMemory, 1000));
        }
    }
    
    
    private boolean testAll(String patternsFile, boolean checkMemory) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy", "-popatterns", patternsFile));
        
        pbo.initPatterns();
        List<String> nonVerifiedNames = new ArrayList<>();  // names of the patterns we cannot verify
        int nTotalPatterns = 0;
        int nWildcardsOrRepetitions = 0;
        int nNotSupported = 0;
        int nVerified = 0;
        for(Pattern pattern:pbo.getPatterns()) {
            nTotalPatterns++;
            if (pattern.hasWildcard() || pattern.hasRepetition()) {
                nWildcardsOrRepetitions++;
                continue;
            }
            
            // Construct the code snippets:
            CodeBase code = new CodeBase(config);
            List<Pattern> instantiated = allPatternInstantiations(pattern, code);
            if (instantiated == null) {
                nNotSupported ++;
                continue;
            }
            System.out.println("instantiated ("+pattern.name+"): " + instantiated.size());
            int nTotalInstantiations = 0;
            int nVerifiedInstantiations = 0;
            for(Pattern instantiatedPattern:instantiated) {
                config.info("Instantiated pattern:\n" + instantiatedPattern);
                nTotalInstantiations++;
                if (!instantiatedPattern.fullyInstantiated()) {
                    continue;
                }
                if (!checkMemory && instantiatedPattern.usesMemory()) {
                    continue;
                }
                if (instantiatedPattern.hasJumps() ||
                    instantiatedPattern.hasCalls()) {
                    continue;
                }

                // Extract the parameters:
                List<String> parameters = getParameters(instantiatedPattern);
                Assert.assertNotNull("parameters is null", parameters);
                List<Integer> flagsToIgnore = getFlagsToIgnore(instantiatedPattern);
                Assert.assertNotNull("flagsToIgnore is null", flagsToIgnore);
    //            System.out.println("flagsToIgnore: " + flagsToIgnore);
                List<CPUConstants.RegisterNames> registersToIgnore = getRegistersToIgnore(instantiatedPattern);
                Assert.assertNotNull("registersToIgnore is null", registersToIgnore);
    //            System.out.println("registersToIgnore: " + registersToIgnore);            
                Assert.assertTrue(evaluatePattern(instantiatedPattern, parameters, 
                                                  flagsToIgnore, registersToIgnore,
                                                  code, instantiatedPattern.usesMemory(), 1000));
                nVerifiedInstantiations++;
            }
            if (nVerifiedInstantiations > 0) {
                nVerified++;
            } else {
                if (pattern.name != null) {
                    nonVerifiedNames.add(pattern.name);
                }
            }
        }
        
        config.info("testAll: nTotalPatterns: " + nTotalPatterns +
                    ", nWildcardsOrRepetitions: " + nWildcardsOrRepetitions +
                    ", nNotSupported: " + nNotSupported +
                    ", nVerified: " + nVerified);
        config.info("testAll: nonVerified patterns: " + nonVerifiedNames);
        
        return true;
    }
    
    
    private boolean evaluatePattern(Pattern p, List<String> parameters, 
                                    List<Integer> flagsToIgnore, 
                                    List<CPUConstants.RegisterNames> registersToIgnore,
                                    CodeBase code, 
                                    boolean checkMemory, int repetitions) throws Exception
    {
        TrackingZ80Memory z80Memory1 = new TrackingZ80Memory(null);
        Z80Core z801 = new Z80Core(z80Memory1, new PlainZ80IO(), new CPUConfig(config));
        z80Memory1.setCPU(z801);
        TrackingZ80Memory z80Memory2 = new TrackingZ80Memory(null);
        Z80Core z802 = new Z80Core(z80Memory2, new PlainZ80IO(), new CPUConfig(config));
        z80Memory2.setCPU(z802);
        
        if (checkMemory) {
            for(int i = 0;i<0x10000;i++) {
                int v = r.nextInt(256);
                z80Memory1.writeByte(i, v);
                z80Memory2.writeByte(i, v);
            }
        }

        for(int i = 0;i<repetitions;i++) {
            if (!evaluatePattern(p, parameters, flagsToIgnore, registersToIgnore, 
                    code, 
                    z80Memory1, z801, z80Memory2, z802,
                    checkMemory)) return false;
        }
        return true;
    }
    
    
    private boolean evaluatePattern(Pattern p, List<String> parameters, 
                                    List<Integer> flagsToIgnore,
                                    List<CPUConstants.RegisterNames> registersToIgnore,
                                    CodeBase code,
                                    TrackingZ80Memory z80Memory1, Z80Core z801,
                                    TrackingZ80Memory z80Memory2, Z80Core z802,
                                    boolean checkMemory) throws Exception
    {
        // randomize the start address from a range:
        int minStartAddress = 0x0000;
        int maxStartAddress = 0x0f00;
        int minStackAddress = 0xc000;
        int maxStackAddress = 0xf000;
        int maxStackSize = 0x0100;
        
        int startAddress = minStartAddress + r.nextInt(maxStartAddress - minStartAddress);
        int stackAddress = minStackAddress + r.nextInt(maxStackAddress - minStackAddress);
                
        // Assign random values to all the input parameters:
        PatternMatch match = new PatternMatch(p, null);
        for(String parameter:parameters) {            
            // give it a random value:
            int v = r.nextInt(0xf000)+0x1000;   // prevent constants from having the same range as where the code is, just in case
            if (parameter.startsWith("?8bitconst")) {
                v = v % 256;
                if (v > 127) {
                    v -= 256;
                }
            }
            match.addVariableMatch(parameter, Expression.constantExpression(v, config));            
//            System.out.println("parameter " + parameter + " = " + v);
        }

        // Verify the constraints once parameters have been given values (to ensure pattern validity):
        // ...
        
//        if (checkMemory) {
//            for(int i = 0;i<0x10000;i++) {
//                int v = r.nextInt(256);
//                z80Memory1.writeByte(i, v);
//                z80Memory2.writeByte(i, v);
//            }
//        }
        
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
        z801.setRegisterValue(CPUConstants.RegisterNames.SP, stackAddress);
        z802.setRegisterValue(CPUConstants.RegisterNames.SP, stackAddress);
        
        // Simulate the pattern and replacement:
        int patternLastAddress = simulateProgram(p.pattern, match, startAddress, z801, z80Memory1, code);
        int replacementLastAddress = simulateProgram(p.replacement, match, startAddress, z802, z80Memory2, code);

        // Check if the program tried to read from itself (we consider this a successful test):
        for(int read[]:z80Memory1.getMemoryReads()) {
            int address = read[0];
            if (address >= startAddress && address < patternLastAddress) {
                return true;
            }
        }
        for(int read[]:z80Memory2.getMemoryReads()) {
            int address = read[0];
            if (address >= startAddress && address < patternLastAddress) {
                return true;
            }
        }
        
        // Compare the output state of the simulator:
        for(CPUConstants.RegisterNames register: CPUConstants.eightBitRegisters) {
            if (registersToIgnore.contains(register)) continue;
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
                System.out.println("Pattern addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(patternLastAddress, 4));
                System.out.println("Replacement addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(replacementLastAddress, 4));
                showInstantiatedProgram("pattern:", p.pattern, match);
                showInstantiatedProgram("replacement:", p.replacement, match);
                return false;
            }
        }
        
        if (checkMemory) {
//            for(int i = 0;i<0x10000;i++) {
            for(Integer i:z80Memory1.getMemoryWrites()) {
                if (i>=startAddress && (i<patternLastAddress || i<replacementLastAddress)) continue;
                if (i>stackAddress - maxStackSize && i <stackAddress + maxStackSize) continue;
                if (z80Memory1.readByteUntracked(i) != z80Memory2.readByteUntracked(i)) {
                    System.out.println("Simulations differ in memory address " + config.tokenizer.toHex(i, 4));
                    System.out.println("Pattern addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(patternLastAddress, 4));
                    System.out.println("Replacement addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(replacementLastAddress, 4));
                    showInstantiatedProgram("pattern:", p.pattern, match);
                    showInstantiatedProgram("replacement:", p.replacement, match);
                    return false;
                }
            }
            for(Integer i:z80Memory2.getMemoryWrites()) {
                if (i>=startAddress && (i<patternLastAddress || i<replacementLastAddress)) continue;
                if (i>stackAddress - maxStackSize && i <stackAddress + maxStackSize) continue;
                if (z80Memory1.readByteUntracked(i) != z80Memory2.readByteUntracked(i)) {
                    System.out.println("Simulations differ in memory address " + config.tokenizer.toHex(i, 4));
                    System.out.println("Pattern addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(patternLastAddress, 4));
                    System.out.println("Replacement addresses: " + config.tokenizer.toHex(startAddress, 4) + " - " + config.tokenizer.toHex(replacementLastAddress, 4));
                    showInstantiatedProgram("pattern:", p.pattern, match);
                    showInstantiatedProgram("replacement:", p.replacement, match);
                    return false;
                }
            }
            // reset memory:
            for(int i = startAddress;(i<patternLastAddress || i<replacementLastAddress);i++) {
                int v = r.nextInt(256);
                z80Memory1.writeByte(i, v);
                z80Memory2.writeByte(i, v);
            }
            z80Memory1.clearMemoryAccessesRandomizingThemSynchronized(z80Memory2);
        }
                
        return true;
    }
    
    
    private void showInstantiatedProgram(String name, List<CPUOpPattern> l, 
                                         PatternMatch match) {
        System.out.println(name);
        for(CPUOpPattern opp:l) {
            if (!opp.wildcard) {
                CPUOp op = opp.instantiate(match, null, config);
                System.out.println("    " + op);
            }
        }
    }
    
    
    private int simulateProgram(List<CPUOpPattern> l, 
                                PatternMatch match,
                                int startAddress,
                                Z80Core z80,
                                TrackingZ80Memory memory,
                                CodeBase code) throws Exception, ProcessorException {
        List<Integer> opAddresses = new ArrayList<>();
        int currentAddress = startAddress;
//        System.out.println("--------");
        for(CPUOpPattern opp:l) {
            if (!opp.wildcard) {
                CPUOp op = opp.instantiate(match, null, config);
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
        }
        memory.clearMemoryAccesses();
        
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
    
    
    
    private List<String> getParameters(Pattern pattern)
    {
        List<String> parameters = new ArrayList<>();
        for(CPUOpPattern opp:pattern.pattern) {
            for(Expression exp:opp.args) {
                exp.getAllSymbols(parameters);
            }
        }
        for(CPUOpPattern opp:pattern.replacement) {
            for(Expression exp:opp.args) {
                exp.getAllSymbols(parameters);
            }
        }
        return parameters;
    }
    
    
    private List<Integer> getFlagsToIgnore(Pattern pattern)
    {
        List<Integer> flags = new ArrayList<>();
        
        for(Pattern.Constraint c:pattern.constraints) {
            switch(c.name) {
                case "flagsNotUsedAfter":
                    for(int i = 1;i<c.args.length;i++) {
                        String flag = c.args[i].replace(" ", "");
                        int found = -1;
                        for(int j = 0;j<CPUConstants.flagNames.length;j++) {
                            if (CPUConstants.flagNames[j].equalsIgnoreCase(flag)) {
                                found = j;
                                break;
                            }
                        }
                        if (found == -1) {
                            System.out.println("Unknown flag " + flag);
                            return null;
                        }
                        flags.add(CPUConstants.flags[found]);
                    }
                    break;
            }
        }

        return flags;
    }
    
    
    private List<CPUConstants.RegisterNames> getRegistersToIgnore(Pattern pattern)
    {
        List<CPUConstants.RegisterNames> registers = new ArrayList<>();
        registers.add(CPUConstants.RegisterNames.R);
        
        for(Pattern.Constraint c:pattern.constraints) {
            switch(c.name) {
                case "regsNotUsedAfter":
                    for(int i = 1;i<c.args.length;i++) {
                        boolean found = false;
                        for(CPUConstants.RegisterNames reg:CPUConstants.allRegisters) {
                            if (CPUConstants.registerName(reg).equalsIgnoreCase(c.args[i])) {
                                if (CPUConstants.is8bitRegister(reg) ||
                                    reg == CPUConstants.RegisterNames.SP ||
                                    reg == CPUConstants.RegisterNames.PC) { 
                                    registers.add(reg);
                                } else {
                                    for(CPUConstants.RegisterNames reg2:CPUConstants.primitive8BitRegistersOf(reg)) {
                                        registers.add(reg2);
                                    }
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            System.out.println("Unknown register " + c.args[i]);
                            return null;
                        }
                    }
                    break;
            }
        }

        return registers;
    }
    
    
    private List<Pattern> allPatternInstantiations(Pattern a_pattern, CodeBase code)
    {
        List<Pattern> open = new ArrayList<>();
        List<Pattern> instantiated = new ArrayList<>();
        Pattern newPattern1 = new Pattern(a_pattern);
        open.add(newPattern1);
        
        while(!open.isEmpty()) {
            Pattern p = open.remove(0);
            List<Pattern> newInstantiated = allPatternInstantiationsOneConstraint(p, code);
            if (newInstantiated == null) {
                instantiated.add(p);
            } else {
                open.addAll(newInstantiated);
            }
        }

        // Check "equal" constraints:
        for(int i = 0;i<instantiated.size();i++) {
            Pattern newPattern2 = instantiated.get(i);
            for(int j = 0;j<newPattern2.constraints.size();j++) {
                Pattern.Constraint c = newPattern2.constraints.get(j);
                if (c.name.equals("equal")) {
                    List<String> tokens = config.tokenizer.tokenize(c.args[0]);
                    if (tokens.size() == 2 && tokens.get(0).equals("?")) {
                        // we can handle this case:

                        // remove this constraint:
                        newPattern2.constraints.remove(c);
                        j--;

                        newPattern2 = newPattern2.assignVariable(c.args[0], c.args[1], code);
                        instantiated.set(i, newPattern2);
                    } else {
                        System.out.println("Equal could not be handled with tokens: " + tokens);
                    }
                }
            }
            
            System.out.println(newPattern2.replacement);
        }    
        
        // Verify all remaining constraints are ok:
        for(Pattern p: instantiated) {
            for(Pattern.Constraint c:p.constraints) {
                switch(c.name) {
                        case "flagsNotUsedAfter":
                        case "regsNotUsedAfter":
                        {
                            int ID = Integer.parseInt(c.args[0]);
                            if (ID == p.pattern.get(p.pattern.size()-1).ID) {
                                // Flags/registers not used after the last instruction in the pattern, are fine
                                continue;
                            }
                            System.out.println("Remaining constraint: " + c.name + " not associated with the last instruction of the pattern, not yet supported");
                            return null;
                        }
                        case "regsNotModified":
                        case "regsNotUsed":
                        case "flagsNotModified":
                        case "flagsNotUsed":
                        case "equal":
                        case "notEqual":
                        case "in":
                        case "notIn":
                        case "regpair":
                        case "reachableByJr":
                        case "evenPushPops":
                        case "atLeastOneCPUOp":
                            System.out.println("Remaining constraint: " + c.name + " not yet supported (args: "+Arrays.toString(c.args)+")");
                            return null;
                        default:
                            System.out.println("Remaining constraint: " + c.name + " not implemented! (args: "+Arrays.toString(c.args)+")");
                            return null;
                }
            }
        }
        return instantiated;
    }
    
    
    private List<Pattern> allPatternInstantiationsOneConstraint(Pattern pattern, CodeBase code)
    {
        String registers[] = {
            "BC", "DE", "HL", "IX", "IY", "SP",
            "A", "B", "C", "D", "E", "H", "L",
            "IXH", "IXL", "IYH", "IYL", "R", "I",
        };
        
        // Process "in"/"notIn" constraints:
        for(Pattern.Constraint c:pattern.constraints) {
            if (c.name.equalsIgnoreCase("in")) {
                List<Pattern> newInstantiated = new ArrayList<>();
                for(int i = 1;i<c.args.length;i++) {
                    // replace "c.args[0]" by "c.args[i]":
                    Pattern p2 = pattern.assignVariable(c.args[0], c.args[i], code);
                    newInstantiated.add(p2);
                }
                return newInstantiated;
            } else if (c.name.equalsIgnoreCase("notIn")) {
                if (c.args[0].startsWith("?reg")) {
                    // "notIn" for a register:
                    List<Pattern> newInstantiated = new ArrayList<>();
                    for(String v:registers) {
                        boolean allowed = true;
                        for(int i = 1;i<c.args.length;i++) {
                            if (v.equalsIgnoreCase(c.args[i])) {
                                allowed = false;
                                break;
                            }
                        }
                        if (allowed) {
                            // replace "c.args[0]" by "c.args[i]":
                            Pattern p2 = pattern.assignVariable(c.args[0], v, code);
                            newInstantiated.add(p2);
                        }
                    }
                    return newInstantiated;
                } else if (c.allArgumentsAreGround(code, config)) {
                    List<Pattern> newInstantiated = new ArrayList<>();
                    for(int i = 1;i<c.args.length;i++) {
                        if (c.args[0].equalsIgnoreCase(c.args[i])) {
                            // constraint violated, we return an empty list to indicate this pattern has no valid instantiations
                            return newInstantiated;
                        }
                    }
                    // Constraint satisfied:
                    pattern.constraints.remove(c);
                    newInstantiated.add(pattern);
                    return newInstantiated;
                }
            }
        }

        // Process "regpair" constraints:
        for(int j = 0;j<pattern.constraints.size();j++) {
            Pattern.Constraint c = pattern.constraints.get(j);
            if (c.name.equals("regpair")) {
                String expected[] = {null, null, null};
                if (!pattern.regpairConstraint(c.args, expected)) {
                    continue;
                }

                List<Pattern> newInstantiated = new ArrayList<>();
                pattern.constraints.remove(c);
                for(int k = 0;k<3;k++) {
                    if (c.args[k].startsWith("?")) {
                        pattern = pattern.assignVariable(c.args[k], expected[k], code);
                    }
                }
                newInstantiated.add(pattern);
                return newInstantiated;
            }
        }
        
        // Get all register variables that remain uninstantiated:
        for(String variable:pattern.getAllVariables()) {
            if (variable.startsWith("?reg")) {
                List<Pattern> newInstantiated = new ArrayList<>();
                for(String v:registers) {
                    Pattern p2 = pattern.assignVariable(variable, v, code);
                    newInstantiated.add(p2);
                }
                return newInstantiated;
            }
        }
        
        return null;
    }    
}

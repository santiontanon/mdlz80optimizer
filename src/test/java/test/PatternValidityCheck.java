/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpDependency;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import util.Pair;
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
    
    private final String patternsToSkip[] = {
        "bc=0-after-ldir", "b=0-after-ldir", "c=0-after-ldir",
    };

    public PatternValidityCheck() {
        config = new MDLConfig();
        pbo = new PatternBasedOptimizer(config);
        config.registerWorker(pbo);
        r = new Random();        
    }

    // Individual pattern tests (not needed as "testAll" already tests them all!):
//    @Test public void test2() throws Exception { test("data/pbo-patterns.txt", "cp02ora", false); }
//    @Test public void test3() throws Exception { test("data/pbo-patterns.txt", "cp12deca", false); }
//    @Test public void test4() throws Exception { test("data/pbo-patterns.txt", "sdcc16bitadd", true); }
//    @Test public void test5() throws Exception { test("data/pbo-patterns.txt", "sdcc16bitcp", true); }
//    @Test public void test6() throws Exception { test("data/pbo-patterns.txt", "move-to-top-of-stack", true); }
//    @Test public void test7() throws Exception { test("data/pbo-patterns.txt", "unnecessary-ld-to-reg", true); }

//    @Test public void test8() throws Exception { test("data/pbo-patterns.txt", "unnecessary-intermediate-reg", true); }
//    @Test public void test9() throws Exception { test("data/pbo-patterns.txt", "unnecessary-double-ex", true); }

    @Test public void testSpeed1() throws Exception { test("data/pbo-patterns-speed.txt", "push2ld", false); }

    // Test all patterns:
    @Test public void testAllNonWildcard() throws Exception { testAll("data/pbo-patterns.txt", true, true, false, true); }
//    @Test public void testAllWildcard() throws Exception { testAll("data/pbo-patterns.txt", false, false, true, true); }  // takes more than 1 minute, so, commented out

    
    private void test(String patternsFile, String patternName, boolean checkMemory) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy", "-popatterns", patternsFile));
        
        pbo.initPatterns();
        Pattern pattern = pbo.getPattern(patternName);
        Assert.assertNotNull(pattern);

//        System.out.println(pattern);
        
        // Construct the code snippets:
        CodeBase code = new CodeBase(config);
        List<Pattern> instantiated = allPatternInstantiations(pattern, code);
        Assert.assertNotNull(instantiated);
//        System.out.println("instantiated ("+patternName+"): " + instantiated.size());
        for(Pattern p:instantiated) {
            if (!p.fullyInstantiated()) {
                continue;
            }
            config.info("Instantiated pattern:\n" + p);

            List<String> parameters = getParameters(p);
            Assert.assertNotNull("parameters is null", parameters);
            Pair<List<Integer>, List<CPUConstants.RegisterNames>> tmp = getFlagsAndRegistersToIgnore(p);
            List<Integer> flagsToIgnore = tmp.left;
            List<CPUConstants.RegisterNames> registersToIgnore = tmp.right;
            Assert.assertNotNull("flagsToIgnore is null", flagsToIgnore);
            Assert.assertNotNull("registersToIgnore is null", registersToIgnore);

            Assert.assertTrue(evaluatePattern(p, parameters, 
                                              flagsToIgnore, registersToIgnore,
                                              code, checkMemory, 1000));
        }
    }
    
    
    private boolean testAll(String patternsFile, boolean testStandard, boolean testRepeat, boolean testWilcard, boolean checkMemory) throws Exception
    {
        Assert.assertTrue(config.parseArgs("dummy", "-popatterns", patternsFile));
        
        pbo.initPatterns();
        List<String> nonVerifiedNames = new ArrayList<>();  // names of the patterns we cannot verify
        List<String> nonSupportedNames = new ArrayList<>();
        int nTotalPatterns = 0;
        int nUntestedDueToWildcards = 0;
        int nUntestedDueToRepetitions = 0;
        int nNotSupported = 0;
        int nVerified = 0;
        for(Pattern pattern:pbo.getPatterns()) {
            nTotalPatterns++;
            if (pattern.hasWildcard()) {
                if (!testWilcard) {
                    nUntestedDueToWildcards++;
                    continue;
                }
            }
            if (pattern.hasRepetition()) {
                if (!testRepeat) {
                    nUntestedDueToRepetitions++;
                    continue;
                }
            }
            if (!pattern.hasWildcard() && !pattern.hasRepetition()) {
                if (!testStandard) continue;
            }
            {
                boolean skip = false;
                for(String toSkip:patternsToSkip) {
                    if (toSkip.equalsIgnoreCase(pattern.name)) {
                        nNotSupported ++;
                        nonSupportedNames.add(toSkip);
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
            }
                                    
            // Construct the code snippets:
            CodeBase code = new CodeBase(config);
            List<Pattern> instantiated = allPatternInstantiations(pattern, code);
            if (instantiated == null) {
                nNotSupported ++;
                if (pattern.name == null) {
                    nonSupportedNames.add("-");
                } else {
                    nonSupportedNames.add(pattern.name);
                }
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

                List<String> parameters = getParameters(instantiatedPattern);
                Assert.assertNotNull("parameters is null", parameters);
                Pair<List<Integer>, List<CPUConstants.RegisterNames>> tmp = getFlagsAndRegistersToIgnore(instantiatedPattern);
                List<Integer> flagsToIgnore = tmp.left;
                List<CPUConstants.RegisterNames> registersToIgnore = tmp.right;
                Assert.assertNotNull("flagsToIgnore is null", flagsToIgnore);
                Assert.assertNotNull("registersToIgnore is null", registersToIgnore);
                Assert.assertTrue(evaluatePattern(instantiatedPattern, parameters, 
                                                  flagsToIgnore, registersToIgnore,
                                                  code, instantiatedPattern.usesMemory(), 1000));
                nVerifiedInstantiations++;
            }
            if (nVerifiedInstantiations > 0) {
                nVerified++;
            } else {
                if (pattern.name == null) {
                    nonVerifiedNames.add("unnamed-pattern");
                } else {
                    nonVerifiedNames.add(pattern.name);
                }
            }
        }
        
        config.info("testAll: nTotalPatterns: " + nTotalPatterns +
                    ", nWildcards: " + nUntestedDueToWildcards +
                    ", nRepetitions: " + nUntestedDueToRepetitions +
                    ", nNotSupported: " + nNotSupported +
                    ", nVerified: " + nVerified);
        config.info("testAll: non-verified patterns: " + nonVerifiedNames);
        config.info("testAll: non-supported patterns: " + nonSupportedNames);
        
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
    
    // 179, 249
    // 179, 249
    private boolean evaluatePattern(Pattern p, List<String> parameters, 
                                    List<Integer> flagsToIgnore,
                                    List<CPUConstants.RegisterNames> registersToIgnore,
                                    CodeBase code,
                                    TrackingZ80Memory z80Memory1, Z80Core z801,
                                    TrackingZ80Memory z80Memory2, Z80Core z802,
                                    boolean checkMemory) throws Exception
    {
        // Get forbidden values from "notIn"/"notEqual" constraints:
        HashMap<String,List<String>> forbiddenValues = new HashMap<>();
        for(Pattern.Constraint c:p.constraints) {
            if (c.name.equalsIgnoreCase("notIn") ||
                c.name.equalsIgnoreCase("notEqual")) {
                if (!forbiddenValues.containsKey(c.args[0])) {
                    forbiddenValues.put(c.args[0], new ArrayList<>());
                }
                for(int i = 1;i<c.args.length;i++) {
                    Integer v = evalArgument(c.args[i], code);
                    if (v == null) {
                        // Ignore this one
//                        System.out.println("Cannot evaluate argument in constraint: " + c);
//                        return false;
                    } else {
                        if (!forbiddenValues.get(c.args[0]).contains("" + v)) {
                            forbiddenValues.get(c.args[0]).add("" + v);
                        }
                    }
                }
            }
        }
        // randomize the start address from a range:
        int minStartAddress = 0x0100;
        int maxStartAddress = 0x0f00;
        int minStackAddress = 0xc000;
        int maxStackAddress = 0xf000;
        int maxStackSize = 0x0100;
        
        int startAddress = minStartAddress + r.nextInt(maxStartAddress - minStartAddress);
        int stackAddress = minStackAddress + r.nextInt(maxStackAddress - minStackAddress);
                
        // Assign random values to all the input parameters:
        PatternMatch match = new PatternMatch(p, null);
        // Determine which of the parameters are labels:
        List<String> labelParameters = new ArrayList<>();
        List<String> nonLabelParameters = new ArrayList<>();
        for(String parameter:parameters) {
            if (isLabel(parameter, p)) {
                labelParameters.add(parameter);
            } else {
                nonLabelParameters.add(parameter);
            }
        }
        int nextLabel = startAddress - 2;  // labels will be in the area right before the code:
        for(String parameter:labelParameters) {
            match.addVariableMatch(parameter, Expression.constantExpression(nextLabel, config));            
            nextLabel -= 2;
        }        
        for(String parameter:nonLabelParameters) {
            // give it a random value:
            int v = r.nextInt(0xf000)+0x1000;   // prevent constants from having the same range as where the code is, just in case
            if (parameter.startsWith("?8bitconst")) {
                v = v % 256;
                if (v > 127) {
                    v -= 256;
                }
            }
            if (forbiddenValues.containsKey(parameter)) {
                for(String fv:forbiddenValues.get(parameter)) {
                    if (fv.equals("" + v)) {
                        return true;  // skip this case
                    }
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
        
        // Set an address that is definitively not inside the program, just in case there is a "ret":
        z80Memory1.writeWord(stackAddress, 0);
        z80Memory2.writeWord(stackAddress, 0);
        
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
                System.out.println("Last PC addresses: " + config.tokenizer.toHex(z801.getProgramCounter(), 4) + ", " + config.tokenizer.toHex(z802.getProgramCounter(), 4));
                System.out.println("Stack address: " + config.tokenizer.toHex(stackAddress, 4));
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
                    System.out.println("Simulations differ in memory address " + config.tokenizer.toHex(i, 4) + " (" + i + ")");
                    System.out.println("Stack address: " + config.tokenizer.toHex(stackAddress, 4));
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
                    System.out.println("Simulations differ in memory address " + config.tokenizer.toHex(i, 4) + " (" + i + ")");
                    System.out.println("Stack address: " + config.tokenizer.toHex(stackAddress, 4));
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
    
    
    // Checks whether "symbol" is used as a label in a jr/jp/call/djnz/etc. instruction in the pattern
    private boolean isLabel(String symbol, Pattern pattern)
    {
        for(CPUOpPattern opp:pattern.pattern) {
            if (opp.opName.equalsIgnoreCase("jr") ||
                opp.opName.equalsIgnoreCase("jp") ||
                opp.opName.equalsIgnoreCase("djnz") ||
                opp.opName.equalsIgnoreCase("call")) {
                Expression exp = opp.args.get(opp.args.size() - 1);
                if (exp.type == Expression.EXPRESSION_SYMBOL) {
                    if (exp.symbolName.equalsIgnoreCase(symbol)) {
                        return true;
                    }
                }
            }
        }
        for(CPUOpPattern opp:pattern.replacement) {
            if (opp.opName.equalsIgnoreCase("jr") ||
                opp.opName.equalsIgnoreCase("jp") ||
                opp.opName.equalsIgnoreCase("djnz") ||
                opp.opName.equalsIgnoreCase("call")) {
                Expression exp = opp.args.get(opp.args.size() - 1);
                if (exp.type == Expression.EXPRESSION_SYMBOL) {
                    if (exp.symbolName.equalsIgnoreCase(symbol)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    
    private void showInstantiatedProgram(String name, List<CPUOpPattern> l, 
                                         PatternMatch match) {
        System.out.println(name);
        for(CPUOpPattern opp:l) {
            if (!opp.isWildcard()) {
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
            if (!opp.isWildcard()) {
                CPUOp op = opp.instantiate(match, null, config);
//                System.out.println("    " + op);
                Assert.assertNotNull(op);
//                System.out.println(config.tokenizer.toHex(startAddress, 4) + ":  " + op);

                // Create a fake statement, just so that we can assembleToBytes when there are relative jumps
                CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_CPUOP, null, null, config);
                s.address = currentAddress;
                List<Integer> bytes = op.assembleToBytes(s, code, config);
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
    
    
    private Pair<List<Integer>, List<CPUConstants.RegisterNames>> getFlagsAndRegistersToIgnore(Pattern pattern)
    {
        List<Integer> flags = new ArrayList<>();
        List<CPUConstants.RegisterNames> registers = new ArrayList<>();
        registers.add(CPUConstants.RegisterNames.R);
        
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
                            config.error("Unknown flag " + flag);
                            return null;
                        }
                        flags.add(CPUConstants.flags[found]);
                    }
                    break;

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
                            config.error("Unknown register " + c.args[i]);
                            return null;
                        }
                    }
                    break;
                case "regFlagEffectsNotUsedAfter":
                    CPUOpPattern opp = null;
                    int idx = Integer.parseInt(c.args[0]);
                    for(CPUOpPattern opp2:pattern.pattern) {
                        if (opp2.ID == idx) {
                            opp = opp2;
                            break;
                        }
                    }
                    if (opp == null) {
                        config.error("constraint refers to unexisting op: " + c);
                        return null;
                    }
                    CPUOp op = opp.instantiate(new PatternMatch(pattern, null), pattern, config);
                        
                    List<CPUOpDependency> regFlagOutputDeps = op.getOutputDependencies();
                    for(CPUOpDependency d:regFlagOutputDeps) {
                        if (d.flag == null && d.register == null) {
                            // there is a non-register/flag effect.
                            // System.out.println("regFlagEffectsNotUsedAfter: " + s + " -> dep failed: " + d);
                        } else {
                            if (d.register != null) {
                                if (d.register.equalsIgnoreCase("I") ||
                                    d.register.equalsIgnoreCase("R")) {
                                    return null;
                                } else {
                                    boolean found = false;
                                    for(CPUConstants.RegisterNames reg:CPUConstants.allRegisters) {
                                        if (CPUConstants.registerName(reg).equalsIgnoreCase(d.register)) {
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
                                        config.error("Unknown register " + d.register);
                                        return null;
                                    }
                                }
                            } else if (d.flag != null) {
                                String flag = d.flag.replace(" ", "");
                                int found = -1;
                                for(int j = 0;j<CPUConstants.flagNames.length;j++) {
                                    if (CPUConstants.flagNames[j].equalsIgnoreCase(flag)) {
                                        found = j;
                                        break;
                                    }
                                }
                                if (found == -1) {
                                    config.error("Unknown flag " + flag);
                                    return null;
                                }
                                flags.add(CPUConstants.flags[found]);
                            }
                        }
                    }
                    break;
            }
        }

        return Pair.of(flags, registers);
    }
    
    
    private List<Pattern> allPatternInstantiations(Pattern a_pattern, CodeBase code)
    {
        List<Pattern> open = new ArrayList<>();
        List<Pattern> instantiated = new ArrayList<>();
        Pattern newPattern1 = new Pattern(a_pattern);
        open.add(newPattern1);
                
        while(!open.isEmpty()) {
            System.out.println("allPatternInstantiations: open: " + open.size());
//            if (open.size() > 10000) {
//                System.out.println("!!!");
//            }
            Pattern p = open.remove(0);
            List<Pattern> newInstantiated = allPatternInstantiationsOneConstraint(p, code);
            if (newInstantiated == null) {
                instantiated.add(p);
            } else {
                open.addAll(newInstantiated);
            }
        }
                
        // Check constraints that can only be checked after the pattern is instantiated:
        List<Pattern> toDelete = new ArrayList<>();
        for(int i = 0;i<instantiated.size();i++) {
            HashMap<Integer, List<CPUConstants.RegisterNames>> registersNotToModify = new HashMap<>();
            HashMap<Integer, List<CPUConstants.RegisterNames>> registersNotToUse = new HashMap<>();
            HashMap<Integer, List<Integer>> flagsNotToModify = new HashMap<>();
            HashMap<Integer, List<Integer>> flagsNotToUse = new HashMap<>();
            Pattern p = instantiated.get(i);
            for(int j = 0;j<p.constraints.size();j++) {
                Pattern.Constraint c = p.constraints.get(j);
                switch(c.name) {
                    case "equal":
                    {
                        List<String> tokens1 = config.tokenizer.tokenize(c.args[0]);
                        List<String> tokens2 = config.tokenizer.tokenize(c.args[1]);
                        if (tokens1.size() == 2 && tokens1.get(0).equals("?")) {
                            // remove this constraint:
                            p.constraints.remove(c);
                            j--;
                            p.replaceParameter(c.args[0], c.args[1]);
                        } else if (tokens2.size() == 2 && tokens2.get(0).equals("?")) {
                            // remove this constraint:
                            p.constraints.remove(c);
                            j--;
                            p.replaceParameter(c.args[1], c.args[0]);
                        } else {
                            Expression exp1 = config.expressionParser.parse(tokens1, null, null, code);
                            if (exp1 != null && exp1.evaluatesToIntegerConstant()) {
                                Expression exp2 = config.expressionParser.parse(tokens2, null, null, code);
                                if (exp2 != null && exp2.evaluatesToIntegerConstant()) {
                                    Integer v1 = exp1.evaluateToInteger(null, code, true);
                                    Integer v2 = exp2.evaluateToInteger(null, code, true);
                                    if (v1 != null && v1.equals(v2)) {
                                        p.constraints.remove(c);
                                        j--;
                                    } else {
                                        // Constraint violated! We do not want this instantiation:
                                        toDelete.add(p);
                                        break;
                                    }
                                } else {
                                    System.out.println("Equal could not be handled with second arg tokens: " + tokens2);
                                }
                            } else {
                                System.out.println("Equal could not be handled with first arg tokens: " + tokens1);
                            }
                        }
                        break;
                    }
                    case "regsNotModified":
                    {
                        int ID = Integer.parseInt(c.args[0]);
                        CPUOpPattern opp = p.getPatternWithId(ID);
                        if (opp.isWildcard()) {
                            if (!registersNotToModify.containsKey(ID)) {
                                registersNotToModify.put(ID, new ArrayList<>());
                            }
                            for(int k = 1;k<c.args.length;k++) {
//                                if (CPUConstants.registerByName(c.args[k]) == null) {
//                                    System.out.println(c.args[k]);
//                                }
                                for(CPUConstants.RegisterNames r2:CPUConstants.primitive8BitRegistersOf(CPUConstants.registerByName(c.args[k]))) {
                                    registersNotToModify.get(ID).add(r2);
                                }
                            }
                            // remove this constraint:
                            p.constraints.remove(c);
                            j--;
                        }
                        break;
                    }
                    case "regsNotUsed":
                    {
                        int ID = Integer.parseInt(c.args[0]);
                        CPUOpPattern opp = p.getPatternWithId(ID);
                        if (opp.isWildcard()) {
                            if (!registersNotToUse.containsKey(ID)) {
                                registersNotToUse.put(ID, new ArrayList<>());
                            }
                            for(int k = 1;k<c.args.length;k++) {
                                for(CPUConstants.RegisterNames r2:CPUConstants.primitive8BitRegistersOf(CPUConstants.registerByName(c.args[k]))) {
                                    registersNotToUse.get(ID).add(r2);
                                }
                            }
                            // remove this constraint:
                            p.constraints.remove(c);
                            j--;
                        }
                        break;
                    }
                    case "flagsNotModified":
                    {
                        int ID = Integer.parseInt(c.args[0]);
                        CPUOpPattern opp = p.getPatternWithId(ID);
                        if (opp.isWildcard()) {
                            if (!flagsNotToModify.containsKey(ID)) {
                                flagsNotToModify.put(ID, new ArrayList<>());
                            }
                            for(int k = 1;k<c.args.length;k++) {
//                                if (CPUConstants.flagByName(c.args[k]) == null) {
//                                    System.out.println(c.args[k]);
//                                }
                                flagsNotToModify.get(ID).add(CPUConstants.flagByName(c.args[k]));
                            }
                            // remove this constraint:
                            p.constraints.remove(c);
                            j--;
                        }
                        break;
                    }
                    case "flagsNotUsed":
                    {
                        int ID = Integer.parseInt(c.args[0]);
                        CPUOpPattern opp = p.getPatternWithId(ID);
                        if (opp.isWildcard()) {
                            if (!flagsNotToUse.containsKey(ID)) {
                                flagsNotToUse.put(ID, new ArrayList<>());
                            }
                            for(int k = 1;k<c.args.length;k++) {
                                flagsNotToUse.get(ID).add(CPUConstants.flagByName(c.args[k]));
                            }
                            // remove this constraint:
                            p.constraints.remove(c);
                            j--;
                        }
                        break;
                    }
                }
            }
//            System.out.println(newPattern2.replacement);
            if (toDelete.contains(p)) continue;
            
            System.out.println("registersNotToModify:" + registersNotToModify);
            System.out.println("registersNotToUse:" + registersNotToUse);
            System.out.println("flagsNotToModify:" + flagsNotToModify);
            System.out.println("flagsNotToUse:" + flagsNotToUse);

            HashMap<Integer, List<CPUOpPattern>> codeToReplaceWildcardsWith = new HashMap<>();
            for(CPUOpPattern opp:p.pattern) {
                int constID = 0;  // used to synthesize new constants
                if (opp.isWildcard()) {
                    List<CPUOpPattern> wildcardOps = new ArrayList<>();
                    CPUConstants.RegisterNames registers[] = {
                        CPUConstants.RegisterNames.A,
                        CPUConstants.RegisterNames.B, CPUConstants.RegisterNames.C,
                        CPUConstants.RegisterNames.D, CPUConstants.RegisterNames.E,
                        CPUConstants.RegisterNames.H, CPUConstants.RegisterNames.L,
//                        RegisterNames.A_ALT, RegisterNames.F_ALT,
//                        RegisterNames.B_ALT, RegisterNames.C_ALT,
//                        RegisterNames.D_ALT, RegisterNames.E_ALT,
//                        RegisterNames.H_ALT, RegisterNames.L_ALT,
                        CPUConstants.RegisterNames.IXH, CPUConstants.RegisterNames.IXL,
                        CPUConstants.RegisterNames.IYH, CPUConstants.RegisterNames.IYL,
                        CPUConstants.RegisterNames.I,
//                        RegisterNames.R, 
                    };
                    // Registers we need to modify:
                    List<CPUConstants.RegisterNames> l = registersNotToModify.get(opp.ID);
                    if (l == null) l = new ArrayList<>();
                    for(CPUConstants.RegisterNames r:registers) {
                        if (!l.contains(r)) {
                            // We need to modify this register:
                            if (r == CPUConstants.RegisterNames.I) {
                                // Special case, we need to push/pop AF
                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": push af", code, config));
                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld a, ?8bitconst__synthetic__" + constID, code, config));
                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld i, a", code, config));
                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": pop af", code, config));
                                constID++;
                            } else {
                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld "+CPUConstants.registerName(r)+", ?8bitconst__synthetic__" + constID, code, config));
                                constID++;                                
                            }
                        }
                    }
                    // Registers we need to read (we ignore this part for now, as it's problenatic to test):
//                    int nextMemoryAddress = 0x4000;  // Something away from the program and away from the stack
//                    l = registersNotToUse.get(opp.ID);
//                    if (l == null) l = new ArrayList<>();
//                    for(CPUConstants.RegisterNames r2:registers) {
//                        if (!l.contains(r2)) {
//                            // We need to use this register:
//                            if (r2 == CPUConstants.RegisterNames.A) {
//                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld ("+nextMemoryAddress+"), a", code, config));
//                                nextMemoryAddress++;
//                            } else {
//                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": push af", code, config));
//                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld a, "+CPUConstants.registerName(r2), code, config));
//                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld (" + nextMemoryAddress + "), a", code, config));
//                                wildcardOps.add(CPUOpPattern.parse(opp.ID + ": pop af", code, config));
//                                nextMemoryAddress++; 
//                            }
//                        }
//                    }
                    // Flags we need to modify:
                    List<Integer> l2 = flagsNotToModify.get(opp.ID);
                    int f_xor_mask = 0xff;  // we will "xor" this to "F" to modify the flags we want
                    if (l2 != null) {
                        for(int f:l2) {
                            f_xor_mask &= ~f;
                        }
                    }
                    if (f_xor_mask != 0) {
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": push bc", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": push af", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": pop bc", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld  a, c", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": xor " + f_xor_mask, code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": ld c, a", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": push bc", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": pop af", code, config));
                        wildcardOps.add(CPUOpPattern.parse(opp.ID + ": pop bc", code, config));
                    }
                    if (!wildcardOps.isEmpty()) {
                        for(CPUOpPattern opp2:wildcardOps) {
                            if (opp2 == null) {
                                return null;
                            }
                        }
                        codeToReplaceWildcardsWith.put(opp.ID, wildcardOps);
                    }
                }
            }
            
            for(Integer ID: codeToReplaceWildcardsWith.keySet()) {
                p.replaceWildcard(ID, codeToReplaceWildcardsWith.get(ID));
            }
            
            // Verify all remaining constraints are ok:
            for(Pattern.Constraint c:p.constraints) {
                switch(c.name) {
                        case "flagsNotUsedAfter":
                        case "regsNotUsedAfter":
                        case "regFlagEffectsNotUsedAfter":
                        {
                            int ID = Integer.parseInt(c.args[0]);
                            if (ID == p.pattern.get(p.pattern.size()-1).ID) {
                                // Flags/registers not used after the last instruction in the pattern, are fine
                                continue;
                            }
                            System.out.println("Remaining constraint: " + c.name + " not associated with the last instruction of the pattern, not yet supported (pattern: " + p.name + ")");
                            return null;
                        }
                        case "notIn":
                        case "notEqual":
                            if (argIsSymbol(c.args[0])) {
                                // ok
                                break;
                            }
                            System.out.println("Remaining constraint: " + c.name + " not yet supported (args: "+Arrays.toString(c.args)+")");
                            return null;
                            
                        case "noStackArguments":
                            // We ignore this constraint
                            break;
                        
                        case "regsNotModified":
                        case "regsNotUsed":
                        case "flagsNotModified":
                        case "flagsNotUsed":
                        case "equal":
                        case "in":
                        case "regpair":
                        case "reachableByJr":
                        case "evenPushPops":
                        case "atLeastOneCPUOp":
                        case "memoryNotWritten":
                            System.out.println("Remaining constraint: " + c.name + " not yet supported (args: "+Arrays.toString(c.args)+")");
                            return null;
                        default:
                            System.out.println("Remaining constraint: " + c.name + " not implemented! (args: "+Arrays.toString(c.args)+")");
                            return null;
                }
            }
        }
        
        for(Pattern p:toDelete) {
            instantiated.remove(p);
        }
        
        // Print the first instantiation:
//        System.out.println(instantiated.get(0));
        
        return instantiated;
    }
    
    
    private boolean argIsSymbol(String arg) {
        List<String> tokens = config.tokenizer.tokenize(arg);
        if (tokens.size() == 2 && tokens.get(0).equals("?")) return true;
        return false;
    }
    
    
    private Integer evalArgument(String arg, CodeBase code) {
        List<String> tokens = config.tokenizer.tokenize(arg);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        return exp.evaluateToInteger(null, code, true);        
    }
    
    
    private List<Pattern> allPatternInstantiationsOneConstraint(Pattern pattern, CodeBase code)
    {
        String registers[] = {
            "BC", "DE", "HL", "IX", "IY", "SP",
            "A", "B", "C", "D", "E", "H", "L",
            "IXH", "IXL", "IYH", "IYL", "R", "I",
        };
        
//        System.out.println("allPatternInstantiationsOneConstraint: #contraints " + pattern.constraints.size() + " (first: " + (pattern.constraints.isEmpty() ? "":pattern.constraints.get(0)) + ")");
//        System.out.println("allPatternInstantiationsOneConstraint:");
//        System.out.println(pattern);
        
        // Get the repetition variables:
        List<String> repetitionVariables = new ArrayList<>();
        for(CPUOpPattern opp:pattern.pattern) {
            if (opp.repetitionVariable != null) {
                if (!repetitionVariables.contains(opp.repetitionVariable)) {
                    repetitionVariables.add(opp.repetitionVariable);
                }
            }
        }
        for(CPUOpPattern opp:pattern.replacement) {
            if (opp.repetitionVariable != null) {
                if (!repetitionVariables.contains(opp.repetitionVariable)) {
                    repetitionVariables.add(opp.repetitionVariable);
                }
            }
        }
        if (!repetitionVariables.isEmpty()) {
            List<Pattern> newInstantiated = new ArrayList<>();
            // Start by instantiating a repetition variable:
            String variable = repetitionVariables.get(0);
            // Instantiate repetition patterns with values from 1 to 5:
            for(int i = 1; i <= 5; i++) {
                Pattern p2 = new Pattern(pattern);
                p2.replaceParameter(variable, "" + i);
                List<CPUOpPattern> newPattern = new ArrayList<>();
                for(int j = 0;j<p2.pattern.size();j++) {
                    if (variable.equals(p2.pattern.get(j).repetitionVariable)) {
                        // repeat it "i" times:
                        for(int k = 0;k<i;k++) {
                            CPUOpPattern opp = new CPUOpPattern(p2.pattern.get(j));
                            opp.repetitionVariable = null;
                            newPattern.add(opp);
                        }
                    } else {
                        newPattern.add(p2.pattern.get(j));
                    }
                }
                p2.pattern = newPattern;
                List<CPUOpPattern> newReplacement = new ArrayList<>();
                for(int j = 0;j<p2.replacement.size();j++) {
                    if (variable.equals(p2.replacement.get(j).repetitionVariable)) {
                        // repeat it "i" times:
                        for(int k = 0;k<i;k++) {
                            CPUOpPattern opp = new CPUOpPattern(p2.replacement.get(j));
                            opp.repetitionVariable = null;
                            newReplacement.add(opp);
                        }
                    } else {
                        newReplacement.add(p2.replacement.get(j));
                    }
                }
                p2.replacement = newReplacement;
                newInstantiated.add(p2);
            }
            return newInstantiated;
        }        
        
        // Process "in"/"notIn" constraints:
        for(Pattern.Constraint c:pattern.constraints) {
            if (c.name.equalsIgnoreCase("in")) {
                List<Pattern> newInstantiated = new ArrayList<>();
                for(int i = 1;i<c.args.length;i++) {
                    // replace "c.args[0]" by "c.args[i]":
                    Pattern p2 = new Pattern(pattern);
                    p2.replaceParameter(c.args[0], c.args[i]);
                    p2.constraints.remove(pattern.constraints.indexOf(c));
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
                            Pattern p2 = new Pattern(pattern);
                            p2.replaceParameter(c.args[0], v);
                            p2.constraints.remove(pattern.constraints.indexOf(c));
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
                        pattern.replaceParameter(c.args[k], expected[k]);
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
                    Pattern p2 = new Pattern(pattern);
                    p2.replaceParameter(variable, v);
                    newInstantiated.add(p2);
                }
                return newInstantiated;
            }
        }
        
        return null;
    }    
}

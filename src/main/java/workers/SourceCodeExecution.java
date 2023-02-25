/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.TrackingZ80Memory;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class SourceCodeExecution implements MDLWorker {

    
    public static class FunctionCallRecord {
        public int stack;
        public long startTime, endTime;
    }


    public static class FunctionTrackRecord {
        public FunctionTrackRecord(String a_userString, int a_address) {
            userString = a_userString;
            address = a_address;
        }
        
        public String userString;
        public int address;
        List<FunctionCallRecord> closed = new ArrayList<>();
        List<FunctionCallRecord> open = new ArrayList<>();
    }
    
    
    MDLConfig config = null;
    String startAddressString = null;
    String endAddressString = null;
    String stepsString = null;
    boolean trace = false;
    List<String> trackFunctionStrings = new ArrayList<>();

    
    public SourceCodeExecution(MDLConfig a_config)
    {
        config = a_config;
    }

    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-e:s <address> <steps>```: executes the source code starting at <address> (address can be number or a label name) for <steps> CPU time units, and displays the changed registers, memory and timing.\n" +
               "- ```-e:u <address-start> <address-end>```: executes the source code starting at <address-start> until reaching <address-end>, and displays the changed registers, memory and timing.\n" +
               "- ```-e:trace```: turns on step-by-step execution logging for ```-e:s``` or ```-e:u``` flags.\n" +
               "- ```-e:track-function <address>```: tracks execution count and time of a function at the specified address (can be a label).";
    }

    @Override
    public String simpleDocString() {
        return "";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-e:s") && flags.size()>=3) {
            flags.remove(0);
            startAddressString = flags.remove(0);
            stepsString = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-e:u") && flags.size()>=3) {
            flags.remove(0);
            startAddressString = flags.remove(0);
            endAddressString = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-e:trace")) {
            flags.remove(0);
            trace = true;
            return true;
        } else if (flags.get(0).equals("-e:track-function") && flags.size()>=2) {
            flags.remove(0);
            trackFunctionStrings.add(flags.remove(0));
            return true;
        }

        return false;
    }

    @Override
    public boolean triggered() {
        return startAddressString != null;
    }

    @Override
    public boolean work(CodeBase code) 
    {
        TrackingZ80Memory z80Memory = new TrackingZ80Memory(null);
        Z80Core z80 = new Z80Core(z80Memory, new PlainZ80IO(), new CPUConfig(config));
        z80Memory.setCPU(z80);
        z80.reset();
        
        // assemble and copy program to z80 memory:
        HashMap<Integer, CodeStatement> instructions = new HashMap<>();
        SourceFile main = code.outputs.get(0).main;
        BinaryGenerator generator = new BinaryGenerator(config);
        List<BinaryGenerator.StatementBinaryEffect> statementBytes = new ArrayList<>();        
        generator.generateStatementBytes(main, code, statementBytes);
        for(BinaryGenerator.StatementBinaryEffect effect:statementBytes) {
            Integer address = effect.s.getAddress(code);
            instructions.put(address, effect.s);
            for(int i = 0;i<effect.bytes.length;i++) {
                int b = effect.bytes[i];
                if (b < 0) b += 256;
                z80Memory.writeByte(address + i, b);
            }
        }
        z80Memory.clearMemoryAccesses();
        
        List<String> tokens = config.tokenizer.tokenize(startAddressString);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        int startAddress = exp.evaluateToInteger(null, code, false);
        List<String> modifiedFlags = new ArrayList<>();
        List<String> modifiedRegisters = new ArrayList<>();
        int endAddress = -1;
        int steps = -1;
        if (stepsString != null) {
            tokens = config.tokenizer.tokenize(stepsString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            steps = exp.evaluateToInteger(null, code, false);
        }
        if (endAddressString != null) {
            tokens = config.tokenizer.tokenize(endAddressString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            endAddress = exp.evaluateToInteger(null, code, false);
        }
        
        // Functions to track:
        List<FunctionTrackRecord> trackFunctions = new ArrayList<>();
        for(String functionString:trackFunctionStrings) {
            tokens = config.tokenizer.tokenize(functionString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            int functionAddress = exp.evaluateToInteger(null, code, false);
            trackFunctions.add(new FunctionTrackRecord(functionString, functionAddress));
        }
        
        // Set program counter:
        z80.setProgramCounter(startAddress);
        
        config.debug("SourceCodeExecution: start address: " + startAddress);
        config.debug("SourceCodeExecution: steps: " + steps);
        
        // Execute!
        int nInstructionsExecuted = 0;
        try {
            while(true) {
                int address = z80.getProgramCounter();
                int sp = z80.getSP();
                
                for(FunctionTrackRecord function:trackFunctions) {
                    // Check for returns:
                    if (!function.open.isEmpty()) {
                        FunctionCallRecord lastCall = function.open.get(function.open.size() - 1);
                        int diff = lastCall.stack - sp;
                        // We need to check >= 32768 in case the stack wraps around the address space.
                        if (diff < 0 || diff >= 32768) {
                            lastCall.endTime = z80.getTStates();
                            function.open.remove(lastCall);
                            function.closed.add(lastCall);
                        }
                    }
                    if (address == function.address) {
                        // We just entered in one of the functions to track!
                        FunctionCallRecord r = new FunctionCallRecord();
                        r.stack = sp;
                        r.startTime = z80.getTStates();
                        r.endTime = -1;
                        function.open.add(r);
                    }
                }
                
                if (steps >= 0 && z80.getTStates() < steps) break;
                if (endAddress >= 0 && z80.getProgramCounter() == endAddress) break;
                
                CodeStatement s = instructions.get(address);
                if (trace) {
                    if (s == null) {
                        config.warn("SourceCodeExecution: execution moved away from provided source code!");
                    } else {
                        config.info("  executing: " + s.toString());
                    }
                }
                if (s != null && s.op != null) {
                    for(String flag:s.op.spec.outputFlags) {
                        if (!modifiedFlags.contains(flag)) {
                            modifiedFlags.add(flag);
                        }
                    }
                    for(String reg:s.op.spec.outputRegs) {
                        if (reg.equalsIgnoreCase("IYq")) reg = "IY";
                        if (reg.equalsIgnoreCase("IXp")) reg = "IX";
                        if (!modifiedRegisters.contains(reg)) {
                            modifiedRegisters.add(reg);
                        }
                    }
                }
                z80.executeOneInstruction();
                nInstructionsExecuted ++;
            }
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        
        // Print changes:
        config.info("Execution result:");
        for(String reg:modifiedRegisters) {
            RegisterNames regName = CPUConstants.registerByName(reg);
            int v = z80.getRegisterValue(regName);
            if (CPUConstants.is8bitRegister(regName)) {
                config.info("  " + reg + ": " + v + " (" + config.tokenizer.toHexByte(v, config.hexStyle) + ")");
            } else {
                config.info("  " + reg + ": " + v + " (" + config.tokenizer.toHexWord(v, config.hexStyle) + ")");
            }
        }
        config.info("  Modified flags: " + modifiedFlags);
        HashSet<Integer> addresses = new HashSet<>();
        for(int address:z80Memory.getMemoryWrites()) {
            if (!addresses.contains(address)) {
                config.info("  (" + config.tokenizer.toHexWord(address, config.hexStyle) + ") = " + 
                        z80Memory.readByte(address) + 
                        " (" + config.tokenizer.toHexByte(z80Memory.readByte(address), config.hexStyle) + ")");
                addresses.add(address);
            }
        }
        config.info("  " + nInstructionsExecuted + " instructions executed.");
        config.info("  execution time: " + z80.getTStates() + " "+config.timeUnit + "s");

        // Report function execution:
        if (!trackFunctions.isEmpty()) {
            config.info("Function tracking count (min/avg/max time):");
        }
        for(FunctionTrackRecord function:trackFunctions) {
            if (!function.closed.isEmpty()) {
                long min = -1;
                long max = -1;
                double average = 0;
                for(FunctionCallRecord r:function.closed) {
                    long time = (r.endTime - r.startTime);
                    if (min == -1 || time < min) min = time;
                    if (max == -1 || time > max) max = time;
                    average += time;
                }
                average /= function.closed.size();
                config.info("- \"" + function.userString + "\": " + function.closed.size() + " (" +
                        min + "/" + average + "/" + max + ")");
            }
            if (!function.open.isEmpty()) {
                config.info("    called " + function.open.size() + " times without returning.");
            }
        }
        return true;
    }
    
}

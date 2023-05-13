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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
        public FunctionTrackRecord trackRecord;
        public int stack;
        public long startTime, endTime;
        List<FunctionCallRecord> subcalls = new ArrayList<>();        
    }


    public static class FunctionTrackRecord {
        public FunctionTrackRecord(String a_userString, int a_address) {
            userString = a_userString;
            address = a_address;
        }
        
        public void clear()
        {
            closed.clear();
            open.clear();
        }
        
        public String userString;
        public int address;
        List<FunctionCallRecord> closed = new ArrayList<>();
        List<FunctionCallRecord> open = new ArrayList<>();
    }
    
    
    MDLConfig config = null;
    String startAddressString = null;
    String endAddressString = null;
    String startTrackingAddressString = null;
    String stepsString = null;
    boolean trace = false;
    boolean trackAllFunctions = false;
    boolean reportAsExecutionTree = false;
    int reportAsExecutionTreeMaxDepth = 0;
    List<String> trackFunctionStrings = new ArrayList<>();
    List<String> ignoreFunctionStrings = new ArrayList<>();

    
    public SourceCodeExecution(MDLConfig a_config)
    {
        config = a_config;
    }

    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-e:s <address-start> <steps>```: executes the source code starting at <address> (address can be number or a label name) for <steps> CPU time units, and displays the changed registers, memory and timing.\n" +
               "- ```-e:u <address-start> <address-end>```: executes the source code starting at <address-start> until reaching <address-end>, and displays the changed registers, memory and timing.\n" +
               "- ```-e:trace```: turns on step-by-step execution logging for ```-e:s``` or ```-e:u``` flags.\n" +
               "- ```-e:track-function <address>```: tracks execution count and time of a function at the specified address (can be a label).\n" +
               "- ```-e:track-all-functions```: tracks execution count and time of all functions in the code (auto detected by looking at all `call` instructions).\n" +
               "- ```-e:ignore <address>```: if during execution, this address is reached, an automatic ```ret``` will be executed, to return. This is useful to ignore BIOS/firmware calls that might not be defined in the codebase.\n" +
               "- ```-e:st <address>```: even if execution will start at ```<address-start>``` as specified in the above flags, function execution time will only be tracked starting at this label (useful if there is some initialization code we do not want to track).\n" + 
               "- ```-e:tree```: reports the result as an execution tree.\n" +
               "- ```-e:tree:n```: reports the result as an execution tree, but only showing ```n``` levels, e.g. ```-e:tree:1```.\n";
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
        } else if (flags.get(0).equals("-e:st") && flags.size()>=2) {
            flags.remove(0);
            startTrackingAddressString = flags.remove(0); 
            return true;
        } else if (flags.get(0).equals("-e:trace")) {
            flags.remove(0);
            trace = true;
            return true;
        } else if (flags.get(0).equals("-e:track-function") && flags.size()>=2) {
            flags.remove(0);
            trackFunctionStrings.add(flags.remove(0));
            return true;
        } else if (flags.get(0).equals("-e:track-all-functions")) {
            flags.remove(0);
            trackAllFunctions = true;
            return true;
        } else if (flags.get(0).equals("-e:ignore") && flags.size()>=2) {
            flags.remove(0);
            ignoreFunctionStrings.add(flags.remove(0));
            return true;
        } else if (flags.get(0).equals("-e:tree")) {
            flags.remove(0);
            reportAsExecutionTree = true;
            return true;
        } else if (flags.get(0).startsWith("-e:tree:")) {
            String levelString = flags.remove(0).substring(8);
            int depth = Integer.parseInt(levelString);
            reportAsExecutionTree = true;
            reportAsExecutionTreeMaxDepth = depth;
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
                
        List<String> modifiedFlags = new ArrayList<>();
        List<String> modifiedRegisters = new ArrayList<>();
        List<String> tokens = config.tokenizer.tokenize(startAddressString);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        Integer startAddress = exp.evaluateToInteger(null, code, true);
        Integer endAddress = -1;
        Integer startTrackingAddress = -1;
        Long startTrackingTime = null;
        int steps = -1;
        if (startAddress == null) {
            config.error("Cannot evaluate start address expression: " + startAddressString);
            return false;
        }
        if (stepsString != null) {
            tokens = config.tokenizer.tokenize(stepsString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            steps = exp.evaluateToInteger(null, code, false);
        }
        if (endAddressString != null) {
            tokens = config.tokenizer.tokenize(endAddressString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            endAddress = exp.evaluateToInteger(null, code, true);
            if (endAddress == null) {
                config.error("Cannot evaluate end address expression: " + endAddressString);
                return false;
            }
        }
        if (startTrackingAddressString != null) {
            tokens = config.tokenizer.tokenize(startTrackingAddressString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            startTrackingAddress = exp.evaluateToInteger(null, code, true);
            if (startTrackingAddress == null) {
                config.error("Cannot evaluate end address expression: " + startTrackingAddressString);
                return false;
            }
        }
        
        // Functions to track:
        List<FunctionTrackRecord> trackFunctions = new ArrayList<>();
        List<FunctionCallRecord> topLevelCalls = new ArrayList<>();
        for(String functionString:trackFunctionStrings) {
            tokens = config.tokenizer.tokenize(functionString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            Integer functionAddress = exp.evaluateToInteger(null, code, true);
            if (functionAddress != null) {
                addFunctionToTrack(trackFunctions, new FunctionTrackRecord(functionString, functionAddress));
            }
        }
        if (trackAllFunctions) {
            // Find all the functions:
            for(SourceFile sf:code.getSourceFiles()) {
                for(CodeStatement s:sf.getStatements()) {
                    if (s.op != null && s.op.isCall()) {
                        Expression exp2 = s.op.getTargetJumpExpression();
                        if (exp2.type == Expression.EXPRESSION_SYMBOL) {
                            Integer functionAddress = exp2.evaluateToInteger(s, code, true);
                            if (functionAddress != null) {
                                addFunctionToTrack(trackFunctions, new FunctionTrackRecord(exp2.symbolName, functionAddress));
                            }
                        }
                    }
                }
            }
        }        
        
        // Write a "ret" to the address of all the functions to ignore, so we return immediately:
        for(String functionString:ignoreFunctionStrings) {
            tokens = config.tokenizer.tokenize(functionString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            Integer functionAddress = exp.evaluateToInteger(null, code, true);
            if (functionAddress != null) {
                z80.getRAM().writeByte(functionAddress, CPUConstants.ret_opcode);
            }
        }
        
        
        // Set program counter:
        z80.setProgramCounter(startAddress);
        
        config.debug("SourceCodeExecution: start address: " + startAddress);
        config.debug("SourceCodeExecution: steps: " + steps);
        
        // Execute!
        int nInstructionsExecuted = 0;
        ArrayList<FunctionCallRecord> trackedCallStack = new ArrayList<>();
        try {
            while(true) {
                int address = z80.getProgramCounter();
                int sp = z80.getSP();
                
                for(FunctionTrackRecord function:trackFunctions) {
                    // Check for returns:
                    FunctionCallRecord lastCall = null;
                    if (!function.open.isEmpty()) {
                        lastCall = function.open.get(function.open.size() - 1);
                        int diff = lastCall.stack - sp;
                        // We need to check >= 32768 in case the stack wraps around the address space.
                        if (diff < 0 || diff >= 32768) {
                            lastCall.endTime = z80.getTStates();
                            function.open.remove(lastCall);
                            function.closed.add(lastCall);
                            while (trackedCallStack.contains(lastCall)) {                                
                                trackedCallStack.remove(trackedCallStack.size()-1);
                            }
                        }
                    }
                    if (address == function.address) {
                        // We just entered in one of the functions to track!
                        FunctionCallRecord r = new FunctionCallRecord();
                        r.trackRecord = function;
                        r.stack = sp;
                        r.startTime = z80.getTStates();
                        r.endTime = -1;
                        function.open.add(r);
                        if (reportAsExecutionTree) {
                            if (trackedCallStack.isEmpty()) {
                                topLevelCalls.add(r);
                            } else {
                                // keep track of the execution tree:
                                trackedCallStack.get(trackedCallStack.size()-1).subcalls.add(r);
                            }
                            trackedCallStack.add(r);
                        }
                    }
                }
                
                if (steps >= 0 && z80.getTStates() < steps) break;
                if (endAddress >= 0 && z80.getProgramCounter() == endAddress) break;
                if (startTrackingAddress >= 0 && z80.getProgramCounter() == startTrackingAddress) {
                    topLevelCalls.clear();
                    for(FunctionTrackRecord tr:trackFunctions) {
                        tr.clear();
                    }
                    startTrackingTime = z80.getTStates();
                }
                                
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
        config.info("  Modified memory positions (only last 32 are shown):");
        for(int address:z80Memory.getMemoryWrites()) {
            if (!addresses.contains(address)) {
                config.info("  (" + config.tokenizer.toHexWord(address, config.hexStyle) + ") = " + 
                        z80Memory.readByte(address) + 
                        " (" + config.tokenizer.toHexByte(z80Memory.readByte(address), config.hexStyle) + ")");
                addresses.add(address);
            }
        }
        long globalTotal = z80.getTStates();
        if (startTrackingTime != null) {
            globalTotal -= startTrackingTime;
        }
        config.info("  " + nInstructionsExecuted + " instructions executed.");
        config.info("  execution time: " + globalTotal + " "+config.timeUnit + "s");

        // Report function execution:
        if (trackFunctions.isEmpty()) return true;
        if (reportAsExecutionTree) {
            // Report the execution tree:
            logExecutionTreeStats(topLevelCalls, 0, globalTotal);
        } else {    
            // Construct and format the funtion execution reporting table:
            config.info("Function: count - percentage - min / avg / max:");
            List<MutablePair<Double, String>> rows = new ArrayList<>();
            // Step 1: function names:
            int max_len = 0;
            int max_closed = 0;
            for(FunctionTrackRecord function:trackFunctions) {
                if (!function.closed.isEmpty()) {
                    rows.add(MutablePair.of(0.0, "- \"" + function.userString + "\":"));
                    if (function.userString.length() > max_len) max_len = function.userString.length();
                    if (function.closed.size() > max_closed) max_closed = function.closed.size();
                }
            }
            // Insert black spaces:
            for(int i = 0;i<rows.size();i++) {
                String row = rows.get(i).getRight();
                while(row.length() < max_len + 5) row += " ";
                rows.get(i).setValue(row);
            }
            
            // Add timing:
            int max_closed_str_length = ("" + max_closed).length();
            int i = 0;
            for(FunctionTrackRecord function:trackFunctions) {
                if (!function.closed.isEmpty()) {
                    MutablePair<Double, String> row = rows.get(i);
                    long min = -1;
                    long max = -1;
                    double functionTotal = 0;
                    for(FunctionCallRecord r:function.closed) {
                        long time = (r.endTime - r.startTime);
                        if (min == -1 || time < min) min = time;
                        if (max == -1 || time > max) max = time;
                        functionTotal += time;
                    }
                    double average = functionTotal / function.closed.size();
                    String closedString = "" + function.closed.size();
                    while(closedString.length() < max_closed_str_length) closedString = " " + closedString;
                    double percentageOfGlobalTotal = 100.0 * functionTotal / globalTotal;
                    row.setLeft(percentageOfGlobalTotal);
                    String percentageString = String.format("%.4f", percentageOfGlobalTotal);
                    while(percentageString.length() < 7) percentageString = " " + percentageString;
                    row.setRight(row.getRight() + " " + closedString + " - " + percentageString + "% - (" +
                                 min + " / " + average + " / " + max + ")");
                    i++;
                }
                // Consider whether I want this info or not
                // if (!function.open.isEmpty()) {
                //     config.info("    called " + function.open.size() + " times without returning.");
                // }
            }
            
            // Sort by percentage of time used:
            Collections.sort(rows, new Comparator<MutablePair<Double, String>>() {
                @Override
                public int compare(MutablePair<Double, String> b1, MutablePair<Double, String> b2) {
                    return -Double.compare(b1.getLeft(), b2.getLeft());
                }
            });
            for(Pair<Double, String> row:rows) {
                config.info(row.getRight());
            }
        }
        return true;
    }
    
    
    static void addFunctionToTrack(List<FunctionTrackRecord> trackFunctions, FunctionTrackRecord record) {
        for(FunctionTrackRecord r:trackFunctions) {
            if (r.address == record.address) {
                return;
            }
        }
        trackFunctions.add(record);
    }
    
    
    Pair<Double, String> logExecutionTreeStats(List<FunctionCallRecord> calls, int indentation, long globalTotal)
    {
        double totalTime = 0.0;
        List<String> differentFunctions = new ArrayList<>();
        for(FunctionCallRecord call:calls) {
            if (!differentFunctions.contains(call.trackRecord.userString)) {
                differentFunctions.add(call.trackRecord.userString);
            }
        }
        // Generate the tree first, and then sort each child:
        List<Pair<Double, String>> subTreeTexts = new ArrayList<>();
        for(String functionName:differentFunctions) {
            long min = -1;
            long max = -1;
            double functionTotal = 0;
            int count = 0;
            List<FunctionCallRecord> subcalls = new ArrayList<>();
            for(FunctionCallRecord r:calls) {
                if (!r.trackRecord.userString.equals(functionName)) continue;
                if (r.endTime < r.startTime) continue;
            
                for(FunctionCallRecord r2:r.subcalls) {
                    subcalls.add(r2);
                }
                long time = (r.endTime - r.startTime);
                if (min == -1 || time < min) min = time;
                if (max == -1 || time > max) max = time;
                functionTotal += time;
                count += 1;
            }
            if (count == 0) continue;
            double average = functionTotal / count;
            double percentageOfGlobalTotal = 100.0 * functionTotal / globalTotal;
            String percentageString = String.format("%.4f", percentageOfGlobalTotal);
            String row = "";
            for(int i = 0; i<indentation; i++) row += "  ";
            row += "- \"" + functionName + "\"\t" + count + "\t" + percentageString + "\t" + 
                   "(" + min + " / " + average + " / " + max + ")";
            totalTime += percentageOfGlobalTotal;
            Pair<Double, String> subTree = logExecutionTreeStats(subcalls, indentation + 1, globalTotal);
            if (!subTree.getRight().isEmpty()) {
                subTreeTexts.add(Pair.of(percentageOfGlobalTotal, row + "\n" + subTree.getRight() + "\n"));
            } else {
                subTreeTexts.add(Pair.of(percentageOfGlobalTotal, row + "\n"));
            }
        }

        String treeText = "";
        // Sort by percentage of time used:
        Collections.sort(subTreeTexts, new Comparator<Pair<Double, String>>() {
            @Override
            public int compare(Pair<Double, String> b1, Pair<Double, String> b2) {
                return -Double.compare(b1.getLeft(), b2.getLeft());
            }
        });
        for(Pair<Double, String> subTree:subTreeTexts) {
            treeText += subTree.getRight();
        }
        // remove trailing new lines:
        while(treeText.contains("\n\n")) {
            treeText = treeText.replace("\n\n", "\n");
        }
    
        if (indentation == 0) {
            config.info("Profiler result:\n" + treeText);
        }
        
        if (reportAsExecutionTreeMaxDepth <= 0 || indentation < reportAsExecutionTreeMaxDepth) {
            return Pair.of(totalTime, treeText);
        } else {
            return Pair.of(totalTime, "");
        }
    }
}

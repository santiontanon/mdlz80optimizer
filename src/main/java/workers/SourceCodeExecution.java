/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpSpec;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import util.microprocessor.MappedTrackingZ80Memory;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class SourceCodeExecution implements MDLWorker {
    public static final int SOURCE_RAM = 0;
    public static final int SOURCE_ROM = 1;

            
    public static class FunctionCallRecord {
        public FunctionTrackRecord trackRecord;
        public int stack;
        public long startTime, endTime;
        List<FunctionCallRecord> subcalls = new ArrayList<>();        
    }


    public static class FunctionTrackRecord {
        public FunctionTrackRecord(String a_userString, String a_address) {
            userString = a_userString;
            address = a_address;
        }
        
        public void clear()
        {
            closed.clear();
            open.clear();
        }
        
        public String userString;
        public String address;  // source:address (where "source" is RAM/ROM/etc.)
        List<FunctionCallRecord> closed = new ArrayList<>();
        List<FunctionCallRecord> open = new ArrayList<>();
    }
        
    
    MDLConfig config = null;
    String startAddressUser = null;
    String endAddressUser = null;
    String startTrackingAddressUser = null;
    String stepsString = null;
    boolean trace = false;
    boolean trackAllFunctions = false;
    boolean reportAsExecutionTree = false;
    boolean reportHotSpots = false;
    int reportAsExecutionTreeMaxDepth = 0;
    int nHotSpotsToShow = 20;
    List<String> trackFunctionStrings = new ArrayList<>();
    List<String> ignoreFunctionStrings = new ArrayList<>();
    Disassembler disassembler;
    String mapperConfigFileName = null;
    
    List<String> watchKeys = new ArrayList<>();

    // Memory configuration:
    int ROM_size = 64*1024;
    int segment_size = 16*1024;
    int binary_source = SOURCE_RAM;
    int initial_mapping[][] = {{0, 0}, {0, 1}, {0, 2}, {0, 3}};
    int RAM_mapper_type = MappedTrackingZ80Memory.NO_MAPPER;
    int ROM_mapper_type = MappedTrackingZ80Memory.NO_MAPPER;
    
//    int ROM_size = 128*1024;
//    int segment_size = 16*1024;
//    int binary_source = SOURCE_ROM;
//    int initial_mapping[][] = {{0, 0}, {1, 0}, {1, 1}, {0, 3}};
//    int RAM_mapper_type = MappedTrackingZ80Memory.NO_MAPPER;
//    int ROM_mapper_type = MappedTrackingZ80Memory.MSX_ASCII16_MAPPER;
 
    
    public SourceCodeExecution(MDLConfig a_config)
    {
        config = a_config;
        disassembler = new Disassembler(config);
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
               "- ```-e:tree:n```: reports the result as an execution tree, but only showing ```n``` levels, e.g. ```-e:tree:1```.\n" +
               "- ```-e:hs```: reports execution hotspots (lines of code that take the most execution time overall). By default, it shows the top 20, use ```-e:hs:n``` to show the top n instead.\n" +
               "- ```-e:watch <watch-key>: every time an instruction that has a comment annotated with the tag ```<watch-key>```, the tollowing comma separated expressions will be evaluated and printed (after instruction execution). For example, if you have an instruction like ```ld a, 1  ; mdl-watch: \"hello\", a```, you can pass ```-e:watch mdl-watch:``` and after that instruction is executed, ```hello, 1``` will be printed. Think of this as having the chance of adding print statements throughout the code. You can specify this argument several times, to print watch statements with different keys.\n" +
               "- ```-e:mapper-config <filename>```: if the binary to be executed requires some sort of memory mapper, it can be specified in a configuration text file. The file contains on config option per line, and should include the following options: " +
               "binary_size: <size in bytes, multiple of page_size>, page_size: <page size in bytes, default is 16384>, ram_mapper_type: <type>, rom_mapper_type: <type> (only 'no_mapper', and 'msx_ascii16_mapper' are currently supported), initial_mapping: source1:segment1, source2:segment2, ... (one pair per each page in RAM, and where source == 0 means RAM, and source == 1 means binary, and segment is the segment within each source). " +
               "Notice that when this option is specified, the binary is assumed to be loaded into a separate ROM (separate from RAM), and that the mapper will be used to let the z80 access the binary data. If this option is not specified, the binary will just be loaded in RAM.\n";
    }

    @Override
    public String simpleDocString() {
        return "";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-e:s") && flags.size()>=3) {
            flags.remove(0);
            startAddressUser = flags.remove(0);
            stepsString = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-e:u") && flags.size()>=3) {
            flags.remove(0);
            startAddressUser = flags.remove(0);
            endAddressUser = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-e:st") && flags.size()>=2) {
            flags.remove(0);
            startTrackingAddressUser = flags.remove(0); 
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
        } else if (flags.get(0).equals("-e:hs")) {
            flags.remove(0);
            reportHotSpots = true;
            return true;
        } else if (flags.get(0).startsWith("-e:hs:")) {
            String levelString = flags.remove(0).substring(6);
            int n = Integer.parseInt(levelString);
            reportHotSpots = true;
            nHotSpotsToShow = n;
            return true;
        } else if (flags.get(0).equals("-e:watch") && flags.size()>=2) {
            flags.remove(0);
            watchKeys.add(flags.remove(0));
            return true;
        } else if (flags.get(0).startsWith("-e:mapper-config") && flags.size()>=2) {
            flags.remove(0);
            mapperConfigFileName = flags.remove(0);
            return true;            
        }

        return false;
    }

    @Override
    public boolean triggered() {
        return startAddressUser != null;
    }

    @Override
    public boolean work(CodeBase code) 
    {
        if (mapperConfigFileName != null) {
            if (!loadMapperConfig(mapperConfigFileName)) {
                return false;
            }
        } 
        
        MappedTrackingZ80Memory z80Memory = new MappedTrackingZ80Memory(null, segment_size, RAM_mapper_type, config, trace);
        try {
            z80Memory.addMemorySource(ROM_size, ROM_mapper_type, true);
        } catch(Exception e) {
            config.error(e.getMessage());
            return false;
        }
        Z80Core z80 = new Z80Core(z80Memory, new PlainZ80IO(), new CPUConfig(config));
        z80Memory.setCPU(z80);
        z80.reset();
        
        // assemble and copy program to z80 memory:
        HashMap<String, CodeStatement> instructions = new HashMap<>();
        HashMap<CodeStatement, String> reverseInstructions = new HashMap<>();
        SourceFile main = code.outputs.get(0).main;
        BinaryGenerator generator = new BinaryGenerator(config);
        List<BinaryGenerator.StatementBinaryEffect> statementBytes = new ArrayList<>();
        generator.generateStatementBytes(main, code, statementBytes);
        {
            int romAddress = 0;
            for(BinaryGenerator.StatementBinaryEffect effect:statementBytes) {
                Integer address = effect.s.getAddress(code);
                if (binary_source == SOURCE_ROM) {
                    address = romAddress;
                }
                instructions.put(binary_source + ":" + address, effect.s);
                reverseInstructions.put(effect.s, binary_source + ":" + address);
                for(int i = 0;i<effect.bytes.length;i++) {
                    int b = effect.bytes[i];
                    if (b < 0) b += 256;
                    z80Memory.writeByteToSource(binary_source, address + i, b);
                }
//                if (effect.bytes.length > 0) {
//                    System.out.println(config.tokenizer.toHex(address, 4) + ": " + Arrays.toString(effect.bytes) + "  ->  " + effect.s + "  ->  " + effect.s.fileNameLineString());
//                }
                romAddress += effect.bytes.length;
            }
        }
        z80Memory.clearMemoryAccesses();
        
        for(int i = 0;i<initial_mapping.length;i++) {
            z80Memory.mapPage(i, initial_mapping[i][0], initial_mapping[i][1]);
        }
                
        List<String> modifiedFlags = new ArrayList<>();
        List<String> modifiedRegisters = new ArrayList<>();
        List<String> tokens = config.tokenizer.tokenize(startAddressUser);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        String startAddressString = functionAddressInBinary(exp, code, reverseInstructions);
        String endAddressString = null;
        String startTrackingAddressString = null;
        Long startTrackingTime = null;
        int steps = -1;
        if (startAddressString == null) {
            config.error("Cannot evaluate start address expression: " + startAddressUser);
            return false;
        }
        if (stepsString != null) {
            tokens = config.tokenizer.tokenize(stepsString);
            exp = config.expressionParser.parse(tokens, null, null, code);
            steps = exp.evaluateToInteger(null, code, false);
        }
        if (endAddressUser != null) {
            tokens = config.tokenizer.tokenize(endAddressUser);
            exp = config.expressionParser.parse(tokens, null, null, code);
            endAddressString = functionAddressInBinary(exp, code, reverseInstructions);
            if (endAddressString == null) {
                config.error("Cannot evaluate end address expression: " + endAddressUser);
                return false;
            }
        }
        if (startTrackingAddressUser != null) {
            tokens = config.tokenizer.tokenize(startTrackingAddressUser);
            exp = config.expressionParser.parse(tokens, null, null, code);
            startTrackingAddressString = functionAddressInBinary(exp, code, reverseInstructions);
            if (startTrackingAddressString == null) {
                config.error("Cannot evaluate end address expression: " + startTrackingAddressUser);
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
                String addressString = ":" + functionAddress;
                if (binary_source == SOURCE_ROM) {
                    addressString = functionAddressInBinary(exp, code, reverseInstructions);
                }
                if (addressString != null) {
                    addFunctionToTrack(trackFunctions, new FunctionTrackRecord(functionString, addressString));
                }
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
                                String addressString = ":" + functionAddress;
                                if (binary_source == SOURCE_ROM) {
                                    addressString = functionAddressInBinary(exp2, code, reverseInstructions);
                                }
                                if (addressString != null) {
                                    addFunctionToTrack(trackFunctions, new FunctionTrackRecord(exp2.symbolName, addressString));
                                }
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
                z80Memory.writeByteToSource(0, functionAddress, CPUConstants.ret_opcode);
            }
        }
        
        // Hotspot tracking:
        HashMap<String, MutablePair<Integer, Integer>> hotspots = new HashMap<>();
        
        // Set program counter:
        Integer startAddress = z80Memory.integerAddressOf(startAddressString);
        if (startAddress == null) {
            config.error("SourceCodeExecution: start address is not mapped to z80 memory at start up!");
            return false;
        }
        z80.setProgramCounter(startAddress);
        
        config.debug("SourceCodeExecution: start address: " + startAddressString);
        config.debug("SourceCodeExecution: end address: " + endAddressString);
        config.debug("SourceCodeExecution: max steps: " + steps);
        
        // Execute!
        int nInstructionsExecuted = 0;
        ArrayList<FunctionCallRecord> trackedCallStack = new ArrayList<>();
        try {
            while(true) {
                int pcAddress = z80.getProgramCounter();
                int sp = z80.getSP();
                String addressString = z80Memory.addressString(pcAddress);
                
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
                    if (addressMatch(pcAddress, addressString, function.address)) {
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
                
                if (steps >= 0 && z80.getTStates() > steps) break;
                if (endAddressString != null && addressMatch(pcAddress, addressString, endAddressString)) break;
                if (startTrackingAddressString != null && addressMatch(pcAddress, addressString, startTrackingAddressString)) {
                    topLevelCalls.clear();
                    for(FunctionTrackRecord tr:trackFunctions) {
                        tr.clear();
                    }
                    startTrackingTime = z80.getTStates();
                }
                                
                CodeStatement s = instructions.get(addressString);
                if (trace) {
                    if (s == null) {
                        CPUOp op = attemptToDisassemble(z80Memory, pcAddress, code);
                        if (op != null) {
                          config.info(z80.getTStates() + "  executing (disassembled from "+addressString+"): " + op);
                        } else {
                          config.warn("SourceCodeExecution: could not disassemble instruction away from provided source code! ("+addressString+")");
                        }
                    } else {
                        config.info(z80.getTStates() + "  executing ("+addressString+"): " + s.toString());
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
                
                long previousTime = z80.getTStates();
                z80.executeOneInstruction();
                
                if (s != null && s.comment != null) {
                    for(String watchKey: watchKeys) {
                        if (s.comment.contains(watchKey)) {
                            int idx = s.comment.indexOf(watchKey) + watchKey.length();
                            String watches = s.comment.substring(idx);
                            printWatchStatements(watches, code, z80);
                        }
                    }
                }
                
                if (reportHotSpots) {
                    int executionTime = (int)(z80.getTStates() - previousTime);
                    if (!hotspots.containsKey(addressString)) {
                        hotspots.put(addressString, MutablePair.of(0, 0));
                    }
                    MutablePair<Integer, Integer> spot = hotspots.get(addressString);
                    spot.setLeft(spot.getLeft() + 1);
                    spot.setRight(spot.getRight() + executionTime);
                }
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
        
        if (!trackedCallStack.isEmpty()) {
            config.info("Execution ended with tracked call stack:");
            for(FunctionCallRecord r:trackedCallStack) {
                config.info("    " + r.trackRecord.userString);
            }
        }
        
        if (reportAsExecutionTree) {
            // Report the execution tree:
            logExecutionTreeStats(topLevelCalls, 0, globalTotal);
        } else {    
            // Construct and format the funtion execution reporting table:
            config.info("Function: count  percentage  (min / avg / max):");
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
                    row.setRight(row.getRight() + " " + closedString + "\t" + percentageString + "%\t(" +
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
        if (reportHotSpots) reportHotspots(hotspots, instructions);
        return true;
    }
    
    
    boolean addressMatch(int pcAddress, String addressString, String addressToMatch) {
        if (addressString.equals(addressToMatch)) return true;
        return addressToMatch.charAt(0) == ':' && addressToMatch.equals(":" + pcAddress);
    }
    
    
    static void addFunctionToTrack(List<FunctionTrackRecord> trackFunctions, FunctionTrackRecord record) {
        for(FunctionTrackRecord r:trackFunctions) {
            if (r.address.equals(record.address)) {
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
            config.info("Profiler result: count  percentage  (min / avg / max)\n" + treeText);
        }
        
        if (reportAsExecutionTreeMaxDepth <= 0 || indentation < reportAsExecutionTreeMaxDepth) {
            return Pair.of(totalTime, treeText);
        } else {
            return Pair.of(totalTime, "");
        }
    }
    
    
    void reportHotspots(HashMap<String, MutablePair<Integer, Integer>> hotspots,
                        HashMap<String, CodeStatement> instructions)
    {
        List<String> sortedSpots = new ArrayList<>();
        sortedSpots.addAll(hotspots.keySet());
        Collections.sort(sortedSpots, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return -Double.compare(hotspots.get(s1).getRight(), hotspots.get(s2).getRight());
            }
        });
        String hotspotsString = "";
        for(int i = 0;i<nHotSpotsToShow;i++) {
            String spot = sortedSpots.get(i);
            MutablePair<Integer, Integer> stats = hotspots.get(spot);
            hotspotsString += instructions.get(spot).fileNameLineString() + "\t" + stats.getLeft() + "\t" + stats.getRight() + "\n";
        }
        config.info("Hotspots: count time\n" + hotspotsString);
    }
    
    
    /*
    If "exp" is a label name, it calculates the offset in the binary that
    corresponds to that label (physical address). Notice that this is different
    from the address of that label (logical address). If the expression is not
    a label, then it just returns the expression value.
    
    The return value is source:address. If the source is not specified (e.g., 
    if the expression is not a label, or if the label is defined as an "equ",
    then source is empty).
    */
    String functionAddressInBinary(Expression exp, CodeBase code, HashMap<CodeStatement, String> instructionsToAddresses)
    {
        if (exp.type != Expression.EXPRESSION_SYMBOL) {
            return ":" + exp.evaluateToInteger(null, code, true);
        }
        SourceConstant label = code.getSymbol(exp.symbolName);
        CodeStatement s = label.definingStatement;
        if (s.type == CodeStatement.STATEMENT_CONSTANT) {
            // Label defined with an "equ" statement:
            return ":" + exp.evaluateToInteger(null, code, true);
        }
        
        while(s != null && s.op == null) {
            while(s.include != null) {
                s = s.include.getStatements().get(0);
            }            
            s = s.source.getNextStatementTo(s, code);
        }
        if (s == null) {
            config.error("functionAddressInBinary: no cpu op after label. exp: " + exp);
            return null;
        }
        if (!instructionsToAddresses.containsKey(s)) {
            config.error("functionAddressInBinary: first function statement not found in mapping! exp: " + exp);
            return null;
        }
        
        return instructionsToAddresses.get(s);
    }
    

    CPUOp attemptToDisassemble(MappedTrackingZ80Memory z80Memory, int pcAddress, CodeBase code)
    {
        // This first loop is slow, but it's ok, as this method is only
        // called if we are printing a trace, which is slow anyway:
        int maxOpSpecSizeInBytes = 1;
        for(CPUOpSpec spec:config.opParser.getOpSpecs()) {
            if (spec.sizeInBytes > maxOpSpecSizeInBytes) {
                maxOpSpecSizeInBytes = spec.sizeInBytes;
            }
        }
        
        List<Integer> data = new ArrayList<>();
        for(int i = 0;i<maxOpSpecSizeInBytes;i++) {
            data.add(z80Memory.readByte(pcAddress + i));
        }
        
        CPUOp op = disassembler.tryToDisassembleSingleInstruction(data, code);
        return op;
    }    
    
    
    void printWatchStatements(String watchesString, CodeBase code, Z80Core z80) {
        List<String> tokens = config.tokenizer.tokenize(watchesString);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        String outputString = "";
        if (exp != null) {
            outputString += printWatchExpression(exp, code, z80);
        }
        while(exp != null && !tokens.isEmpty()) {
            if (tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
            exp = config.expressionParser.parse(tokens, null, null, code);
            if (exp != null) {
                String expString = printWatchExpression(exp, code, z80);
                if (!expString.isEmpty()) {
                    outputString += ", " + expString;
                }
            }
        }
        config.info(outputString);
    }
    
    
    String printWatchExpression(Expression exp, CodeBase code, Z80Core z80) {
        if (exp.evaluatesToStringConstant()) {
            String v = exp.evaluateToString(null, code, false);
            return "" + v;
        } else if (exp.evaluatesToIntegerConstant()) {
            Integer v = exp.evaluateToInteger(null, code, false);
            return "" + v;
        } else if (exp.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
            if (CodeBase.isRegister(exp.registerOrFlagName)) {
                RegisterNames reg = CPUConstants.registerByName(exp.registerOrFlagName);
                return "" + z80.getRegisterValue(reg);
            } else {
                // it's a flag:
                int flag = CPUConstants.flagByName(exp.registerOrFlagName);
                return "" + z80.getFlagValue(flag);
            }
        } else {
            config.warn("SourceCodeExecution: cannot evaluate watch expression " + exp.toString());
        }
        return "";
    }
    

    boolean loadMapperConfig(String mapperConfigFileName) {
        try {
            CodeBase code = new CodeBase(config);
            BufferedReader br = new BufferedReader(new FileReader(mapperConfigFileName));
            String line = br.readLine();
            while(line != null) {
                line = line.strip().toLowerCase();
                if (line.startsWith("binary_size:")) {
                    line = line.substring(12);
                    List<String> tokens = config.tokenizer.tokenize(line);
                    Expression exp = config.expressionParser.parse(tokens, null, null, code);
                    Integer v = exp.evaluateToInteger(null, code, true);
                    if (v == null) {
                        config.error("Cannot evaluate expression: " + line);
                        return false;
                    }
                    ROM_size = v;
                } else if (line.startsWith("page_size:")) {
                    line = line.substring(10);
                    List<String> tokens = config.tokenizer.tokenize(line);
                    Expression exp = config.expressionParser.parse(tokens, null, null, code);
                    Integer v = exp.evaluateToInteger(null, code, true);
                    if (v == null) {
                        config.error("Cannot evaluate expression: " + line);
                        return false;
                    }
                    segment_size = v;
                } else if (line.startsWith("initial_mapping:")) {
                    line = line.substring(16);
                    String parts[] = line.split(",");
                    int nPages = 64*1024 / segment_size;
                    if (nPages != parts.length) {
                        config.error("Found " + parts.length + " page mappings, but expected " + nPages + " in: " + line);
                        return false;
                    }
                    initial_mapping = new int[nPages][2];                    
                    for(int i = 0;i<nPages;i++) {
                        String parts2[] = parts[i].split(":");
                        if (parts2.length != 2) {
                            config.error("Cannot parse page mapping " + parts[i] + " in: " + line);
                            return false;
                        }
                        Integer source = Integer.parseInt(parts2[0].strip());
                        Integer segment = Integer.parseInt(parts2[1].strip());
                        if (source == null || segment == null) {
                            config.error("Invalid source or segment numbers in page mapping " + parts[i] + " in: " + line);
                            return false;
                        }
                        initial_mapping[i][0] = source;
                        initial_mapping[i][1] = segment;
                    }
                } else if (line.startsWith("ram_mapper_type:")) {
                    line = line.substring(16).strip();
                    switch(line) {
                        case "no_mapper":
                            RAM_mapper_type = MappedTrackingZ80Memory.NO_MAPPER;
                            break;
                        case "msx_ascii16_mapper":
                            RAM_mapper_type = MappedTrackingZ80Memory.MSX_ASCII16_MAPPER;
                            break;
                        default:
                            config.error("Unsupported mapper type: " + line);
                            return false;
                    }
                } else if (line.startsWith("binary_mapper_type:")) {
                    line = line.substring(19).strip();
                    switch(line) {
                        case "no_mapper":
                            ROM_mapper_type = MappedTrackingZ80Memory.NO_MAPPER;
                            break;
                        case "msx_ascii16_mapper":
                            ROM_mapper_type = MappedTrackingZ80Memory.MSX_ASCII16_MAPPER;
                            break;
                        default:
                            config.error("Unsupported mapper type: " + line);
                            return false;
                    }
                } else {
                    config.error("Cannot parse line: " + line);
                }
                line = br.readLine();
            }
            // When we specify a mapping, we assume the binary is always in ROM:
            binary_source = SOURCE_ROM;
        } catch(IOException e) {
            config.error(e.getMessage());
            return false;
        }
        return true;
    }
    
}

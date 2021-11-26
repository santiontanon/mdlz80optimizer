/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import cl.OptimizationResult;
import code.CPUOp;
import code.CPUOpDependency;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import parser.SourceLine;
import util.microprocessor.IMemory;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.PlainZ80Memory;
import util.microprocessor.TrackingZ80Memory;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;
import workers.MDLWorker;
import workers.pattopt.Pattern;

/**
 *
 * @author santi
 */
public class SearchBasedOptimizer implements MDLWorker {    
    public static final String SBO_RESULT_KEY = "Search-based optimizer optimizations";
    
//    public static final int MAX_NUMBER_OF_OPTIMIZATIONS = -1;  // Only used for debugging
    
    // Type of operation to perform:
    public static final int SBO_GENERATE = 0;
    public static final int SBO_OPTIMIZE = 1;
    
    // Type of search to be done:
    public static final int SEARCH_ID_OPS = 0;  // Iterative deepening by # of ops
    public static final int SEARCH_ID_BYTES = 1;  // Iterative deepening by # of bytes
    public static final int SEARCH_ID_CYCLES = 2;  // Iterative deepning by # cycles
    
    public static final int SEARCH_TIME_WORST = 0;
    public static final int SEARCH_TIME_BEST = 1;
    public static final int SEARCH_TIME_AVERAGE = 2;
    
    Random random = new Random();
    MDLConfig config;
    MDLConfig internalConfig;  // an alternative config to parse ops using default z80 syntax
    
    boolean trigger = false;
    int operation = SBO_GENERATE;
    boolean showNewBestDuringSearch = true;
    boolean silentSearch = false;
    
    // Search configuration parameters if specified via flags (will overwrite
    // those in the Specification file):
    int flags_searchType = -1;
    int flags_codeStartAddress = -1;
    int flags_maxSimulationTime = -1;
    int flags_maxSizeInBytes = -1;
    int flags_maxOps = -1;
    int flags_nThreads = -1;
    int flags_nChecks = -1;
    
    int optimization_max_block_size = 2;
    
    public final int DEPTH_TO_PRECOMPUTE3 = 4;
    public final int SIZE_TO_PRECOMPUTE3 = 7;
    public final int TIME_TO_PRECOMPUTE3 = 4;
    HashMap<Integer, Integer> precomputeScheduleDepth = new HashMap<>();
    HashMap<Integer, Integer> precomputeScheduleSize = new HashMap<>();
    HashMap<Integer, Integer> precomputeScheduleTime = new HashMap<>();
    
    // Cache to accelerate computations:
    List<RegisterNames> registersUsedAfter_previous = null;
        
    
    public SearchBasedOptimizer(MDLConfig a_config)
    {
        precomputeScheduleDepth.put(3, 2);
        precomputeScheduleDepth.put(DEPTH_TO_PRECOMPUTE3, 3);
        precomputeScheduleSize.put(3, 2);
        precomputeScheduleSize.put(SIZE_TO_PRECOMPUTE3, 3);
        precomputeScheduleTime.put(3, 2);
        precomputeScheduleTime.put(TIME_TO_PRECOMPUTE3, 3);
        config = a_config;
        
        internalConfig = new MDLConfig();
        internalConfig.labelsHaveSafeValues = config.labelsHaveSafeValues;
        try {
            internalConfig.parseArgs("dummy", "-dialect", "mdl", "-cpu", config.cpu);
        } catch(IOException e) {
            config.error("Problem initializing the SearchBasedOptimizer!");
        }
        internalConfig.logger = config.logger;
        
    }
    
    
    @Override
    public String docString() {
        return "- ```-so```: Runs the search-based-based optimizer (if the input file is an assembler file (.asm/.z80/.a80), it'll try to optimize it; if the input file is a specification file (.txt), it will use as a target for program generation; which of the two will be auto-detected based on the file extension). You can pass an optional parameter: ````-so size```, ```-so speed```, or ```-so ops```, to tell the optimizer to optimize for program size, execution speed, or number of instructions. This will overwrite whatever is specified in the specificaiton file (default is to optimize by number of ops).\n" +
               "- ```-so-gen```: Like above, but instead of autodetecting, it always assumes the input file is a specification file for program generation.\n" +
               "- ```-so-opt```: Like above, but instead of autodetecting, it always assumes the input file is an assembler file for optimization.\n" +
               "- ```-so-maxops <n>```: (only for program generation) Sets the upper limit of how many CPU ops the resulting program can have.\n" +
               "- ```-so-maxsize <n>```: (only for program generation) Sets the maximum number of bytes the resulting program can occupy.\n" +
               "- ```-so-maxtime <n>```: (only for program generation) Sets the maximum time (in whichever units the target CPU uses) that the resulting program can take to execute.\n" +
               "- ```-so-threads <n>```: Sets the number of threads to use during search (default value is the number of cores of the CPU).\n" +
               "- ```-so-checks <n>```: Sets the number of random solution checks to consider a solution valid (default is 10000). Higher means more safety, but slower. If this is too low, the optimizer might generate wrong code by chance.\n" +
               "- ```-so-blocksize <n>```: (only for existing assembler optimization) Blocks of this number of instructions will be taken one at a time and optimized (default is 2).\n";
    }

    
    @Override
    public String simpleDocString() {
        return "- ```-so```: Runs the search-based-based optimizer (optimizes code if the input is an assembler file; generates code if the input file is a specification file).\n";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-so") ||
            flags.get(0).equals("-so-gen") ||
            flags.get(0).equals("-so-opt")) {
            String originalFlag = flags.remove(0);
            if (!flags.isEmpty()) {
                if (flags.get(0).equals("size")) {
                    flags_searchType = SEARCH_ID_BYTES;
                    flags.remove(0);
                } else if (flags.get(0).equals("speed")) {
                    flags_searchType = SEARCH_ID_CYCLES;
                    flags.remove(0);
                } else if (flags.get(0).equals("ops")) {
                    flags_searchType = SEARCH_ID_OPS;
                    flags.remove(0);
                }
            }
            trigger = true;
            if (originalFlag.equals("-so-gen") ||
                config.inputFile.endsWith(".txt")) {
                operation = SBO_GENERATE;
                config.codeSource = MDLConfig.CODE_FROM_SEARCHBASEDOPTIMIZER;
            } else if (originalFlag.equals("-so-opt") ||
                       config.inputFile.toLowerCase().endsWith(".asm") ||
                       config.inputFile.toLowerCase().endsWith(".z80") ||
                       config.inputFile.toLowerCase().endsWith(".a80")) {
                operation = SBO_OPTIMIZE;
                config.codeSource = MDLConfig.CODE_FROM_INPUT_FILE;                
            } else {
                config.error("Could not autodetect whether '" + config.inputFile + "' is a specification file or an assembler file. Use '-so-gen'/'-'so-opt' instead of '-so' to disambiguate.");
                return false;
            }
            return true;
        }
        if (flags.get(0).equals("-so-maxops") && flags.size()>=2) {
            flags.remove(0);
            String tmp = flags.remove(0);
            if (!config.tokenizer.isInteger(tmp)) {
                config.error("Invalid argument to -so-maxops: " + tmp);
                return false;
            }
            flags_maxOps = Integer.parseInt(tmp);
            return true;
        }
        if (flags.get(0).equals("-so-maxsize") && flags.size()>=2) {
            flags.remove(0);
            String tmp = flags.remove(0);
            if (!config.tokenizer.isInteger(tmp)) {
                config.error("Invalid argument to -so-maxsize: " + tmp);
                return false;
            }
            flags_maxSizeInBytes = Integer.parseInt(tmp);
            return true;
        }
        if (flags.get(0).equals("-so-maxtime") && flags.size()>=2) {
            flags.remove(0);
            String tmp = flags.remove(0);
            if (!config.tokenizer.isInteger(tmp)) {
                config.error("Invalid argument to -so-maxtime: " + tmp);
                return false;
            }
            flags_maxSimulationTime = Integer.parseInt(tmp);
            return true;
        }
        if (flags.get(0).equals("-so-threads") && flags.size()>=2) {
            flags.remove(0);
            String tmp = flags.remove(0);
            if (!config.tokenizer.isInteger(tmp)) {
                config.error("Invalid argument to -so-threads: " + tmp);
                return false;
            }
            flags_nThreads = Integer.parseInt(tmp);
            if (flags_nThreads <= 0) {
                config.error("Invalid argument to -so-threads: " + tmp + " (number of threads must be a positive integer)");
            }
            return true;
        }
        if (flags.get(0).equals("-so-checks") && flags.size()>=2) {
            flags.remove(0);
            String tmp = flags.remove(0);
            if (!config.tokenizer.isInteger(tmp)) {
                config.error("Invalid argument to -so-checks: " + tmp);
                return false;
            }
            flags_nChecks = Integer.parseInt(tmp);
            if (flags_nChecks <= 0) {
                config.error("Invalid argument to -so-checks: " + tmp + " (number of random checks must be a positive integer)");
            }
            return true;
        }
        if (flags.get(0).equals("-so-blocksize") && flags.size()>=2) {
            flags.remove(0);
            String tmp = flags.remove(0);
            if (!config.tokenizer.isInteger(tmp)) {
                config.error("Invalid argument to -so-blocksize: " + tmp);
                return false;
            }
            optimization_max_block_size = Integer.parseInt(tmp);
            if (optimization_max_block_size <= 0) {
                config.error("Invalid argument to -so-blcksize: " + tmp + " (block size must be a positive integer)");
            }
            return true;
        }
        return false;    
    }

    
    @Override
    public boolean triggered() {
        return trigger;
    }

    
    @Override
    public boolean work(CodeBase a_code) {
        if (operation == SBO_GENERATE) {
            return workGenerate(a_code);
        } else {
            return workOptimize(a_code);
        }
    }
    
    
    private boolean workGenerate(CodeBase code) {
        // Parse specification file:
        Specification spec = SpecificationParser.parse(config.inputFile, code, config);
        if (spec == null) {
            config.error("Cannot parse the input specification file '"+config.inputFile+"'");
            return false;
        }
        
        // Overwrite specification if flags are present:
        if (flags_searchType >= 0) spec.searchType = flags_searchType;
        if (flags_codeStartAddress >= 0) spec.codeStartAddress = flags_codeStartAddress;
        if (flags_maxSimulationTime >= 0) spec.maxSimulationTime = flags_maxSimulationTime;
        if (flags_maxSizeInBytes >= 0) spec.maxSizeInBytes = flags_maxSizeInBytes;
        if (flags_maxOps >= 0) spec.maxOps = flags_maxOps;
        if (flags_nChecks >= 0) spec.numberOfRandomSolutionChecks = flags_nChecks;

        SourceFile sf = new SourceFile("autogenerated.asm", null, null, code, config);
        code.addOutput(null, sf, 0);

        if (!spec.precomputeTestCases(code, config)) {
            config.error("Unable to precompute test cases");
            return false;
        }
        
        SequenceFilter filter = new SequenceFilter(internalConfig);
        filter.setSpecification(spec);
        try {
            filter.loadEquivalences("data/equivalencies-l1.txt");
            filter.loadEquivalences("data/equivalencies-l2-to-l1.txt");
            filter.loadEquivalences("data/equivalencies-l2.txt");
        }catch(Exception e) {
            config.error("Could not load equivalences files...: " + e.getMessage());
            config.error(Arrays.toString(e.getStackTrace()));
            return false;
        }
        
        List<CPUOpDependency> allDependencies = spec.precomputeAllDependencies();        
                
        return workGenerate(spec, filter, allDependencies, sf, code);
    }
    
    private boolean workGenerate(Specification spec, 
                                 SequenceFilter filter,
                                 List<CPUOpDependency> allDependencies,
                                 SourceFile sf,
                                 CodeBase code) {
        config.debug("workGenerate: search parameters: " + spec.searchType + ", " + spec.maxOps + ", " + spec.maxSizeInBytes + ", " + spec.maxSimulationTime);
        
        // Precompute all the op dependencies before search:
        int nDependencies = allDependencies.size();
        config.debug("workGenerate: nDependencies: " + nDependencies);
                
        List<SBOCandidate> allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, 1, internalConfig);
        if (allCandidateOps == null) return false;
        
//        System.out.println("goalDependencies: " + Arrays.toString(spec.goalDependencies));
//        System.out.println("goalDependenciesSatisfiedFromTheStart: " + Arrays.toString(spec.goalDependenciesSatisfiedFromTheStart));
        
        // Precompute which registers and memory addresses to randomize:
        RegisterNames eightBitRegistersToRandomize[];
        int memoryAddressesToRandomize[];
        {
            List<RegisterNames> toRandomize = new ArrayList<>();
            List<Integer> addressToRandomize = new ArrayList<>();
            // Add all the registers in the goal (to prevent spurious matches):
            for(SpecificationExpression exp:spec.goalState) {
                if (exp.leftRegister != null) {
                    for(RegisterNames reg:CPUConstants.primitive8BitRegistersOf(exp.leftRegister)) {
                        if (!toRandomize.contains(reg)) {
                            toRandomize.add(reg);
                        }
                    }
                    if (spec.allowGhostRegisters) {
                        for(RegisterNames reg:CPUConstants.ghost8BitRegistersOf(exp.leftRegister)) {
                            if (!toRandomize.contains(reg)) {
                                toRandomize.add(reg);
                            }
                        }
                    }
                } else if (exp.leftConstantMemoryAddress != null) {
                    addressToRandomize.add(exp.leftConstantMemoryAddress);
                }
            }
            // Remove the ones that will be initialized already in the start state:
            for(SpecificationExpression exp:spec.startState) {
                if (exp.leftRegister != null) {
                    for(RegisterNames reg:CPUConstants.primitive8BitRegistersOf(exp.leftRegister)) {
                        toRandomize.remove(reg);
                    }
                } else if (exp.leftConstantMemoryAddress != null) {
                    if (addressToRandomize.contains(exp.leftConstantMemoryAddress)) {
                        addressToRandomize.remove(exp.leftConstantMemoryAddress);
                    }
                }
            }
            eightBitRegistersToRandomize = new RegisterNames[toRandomize.size()];
            for(int i = 0;i<toRandomize.size();i++) {
                eightBitRegistersToRandomize[i] = toRandomize.get(i);
            }
            memoryAddressesToRandomize = new int[addressToRandomize.size()];
            for(int i = 0;i<addressToRandomize.size();i++) {
                memoryAddressesToRandomize[i] = addressToRandomize.get(i);
            }
        }
                
        SBOGlobalSearchState state = new SBOGlobalSearchState();
        boolean goalDependencies[] = spec.getGoalDependencies(allDependencies);
        int nopDuration = config.opParser.getOpSpecs("nop").get(0).times[0];
        int nThreads = Runtime.getRuntime().availableProcessors();
        if (flags_nThreads != -1) nThreads = flags_nThreads;
//        nThreads = 1;
        SBOExecutionThread threads[] = new SBOExecutionThread[nThreads];
        long start = System.currentTimeMillis();
        
        try {
            if (spec.searchType == SEARCH_ID_OPS) {
                int codeMaxAddress = spec.codeStartAddress + spec.maxSizeInBytes;
                for(int depth = 0; depth<=spec.maxOps; depth++) {
                    if (precomputeScheduleDepth.containsKey(depth)) {
                        // Increase precomputation (not worth it before this point):
                        allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, precomputeScheduleDepth.get(depth), config);
                        if (allCandidateOps == null) return false;                        
//                    } else if (depth == 6) {
//                        // Increase precomputation (not worth it before this point):
//                        allCandidateOps = precomputeCandidateOps(spec, allDependencies, code, 4);
//                        if (allCandidateOps == null) return false;                        
                    }
                    state.init(allCandidateOps, depth==0);
                    for(int i = 0;i<nThreads;i++) {
                        threads[i] = new SBOExecutionThread("thread-" + i, 
                                            spec, allDependencies, goalDependencies,
                                            state, eightBitRegistersToRandomize,
                                            memoryAddressesToRandomize,
                                            showNewBestDuringSearch, code, config,
                                            spec.searchType, depth, codeMaxAddress, spec.maxSimulationTime);
                        threads[i].start();
                    }
                    for(int i = 0;i<nThreads;i++) threads[i].join();
                    if (state.bestOps != null) break;
                    String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
                    if (silentSearch) {
                        config.debug("SearchBasedOptimizer: depth "+depth+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                    } else {
                        config.info("SearchBasedOptimizer: depth "+depth+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                    }
                }

            } else if (spec.searchType == SEARCH_ID_BYTES) {
                for(int size = 0; size<=spec.maxSizeInBytes; size++) {
                    if (precomputeScheduleSize.containsKey(size)) {
                        // Increase precomputation (not worth it before this point):
                        allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, precomputeScheduleSize.get(size), config);
                        if (allCandidateOps == null) return false;                        
                    }
                    state.init(allCandidateOps, size==0);
                    for(int i = 0;i<nThreads;i++) {
                        threads[i] = new SBOExecutionThread("thread-" + i, 
                                            spec, allDependencies, goalDependencies,
                                            state, eightBitRegistersToRandomize,
                                            memoryAddressesToRandomize,
                                            showNewBestDuringSearch, code, config,
                                            spec.searchType, spec.maxOps, spec.codeStartAddress + size, spec.maxSimulationTime);
                        threads[i].start();
                    }
                    for(int i = 0;i<nThreads;i++) threads[i].join();
                    if (state.bestOps != null) break;
                    String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
                    if (!silentSearch) config.info("SearchBasedOptimizer: size "+size+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                }

            } else if (spec.searchType == SEARCH_ID_CYCLES) {
                for(int maxTime = 0; maxTime<=spec.maxSimulationTime; maxTime++) {
                    if ((maxTime % nopDuration == 0) && 
                        precomputeScheduleSize.containsKey(maxTime/nopDuration)) {
                        // Increase precomputation (not worth it before this point):
                        allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, precomputeScheduleSize.get(maxTime/nopDuration), config);
                        if (allCandidateOps == null) return false;                        
                    }
                    state.init(allCandidateOps, maxTime==0);
                    for(int i = 0;i<nThreads;i++) {
                        threads[i] = new SBOExecutionThread("thread-" + i, 
                                            spec, allDependencies, goalDependencies,
                                            state, eightBitRegistersToRandomize,
                                            memoryAddressesToRandomize,
                                            showNewBestDuringSearch, code, config,
                                            spec.searchType, spec.maxOps, spec.codeStartAddress + spec.maxSizeInBytes, maxTime);
                        threads[i].start();
                    }
                    for(int i = 0;i<nThreads;i++) threads[i].join();
                    if (state.bestOps != null) break;
                    String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
                    if (!silentSearch) config.info("SearchBasedOptimizer: time "+maxTime+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                }
                
            } else {
                config.error("Unsupported search type " + spec.searchType);
                return false;
            }
        } catch (Exception e) {
            config.error("Exception while executing the search-based optimizer: " + e.getLocalizedMessage());
            config.error("Exception while executing the search-based optimizer: " + Arrays.toString(e.getStackTrace()));
            return false;
        }
            
        if (state.bestOps == null) {
            if (!silentSearch) config.error("No program that satisfied the specification was found.");
            return false;
        }
        
        int lineNumber = 1;
        for(CPUOp op:state.bestOps) {
            SourceLine sl = new SourceLine("    " + op.toString(), sf, lineNumber);
            CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, sf, config);
            s.op = op;
            sf.getStatements().add(s);
            lineNumber++;
        }
        
        String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
        if (!silentSearch) config.info("SearchBasedOptimizer: search ended ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                
        return true;
    }
    
    
    private boolean workOptimize(CodeBase code) {
        silentSearch = true;
        showNewBestDuringSearch = false;
        // We will never precompute to depth 3 in this case, as the instruction set is too large, and it takes too long:
        precomputeScheduleDepth.remove(DEPTH_TO_PRECOMPUTE3);
        precomputeScheduleSize.remove(SIZE_TO_PRECOMPUTE3);
        precomputeScheduleTime.remove(TIME_TO_PRECOMPUTE3);
        
        OptimizationResult r = new OptimizationResult();
        
        for (SourceFile f : code.getSourceFiles()) {
            HashMap<Integer,HashMap<String,Integer>> previousKnownRegisterValues = new HashMap<>();
            HashMap<String, Integer> knownRegisterValues = new HashMap<>();
            registersUsedAfter_previous = null;
            for (int i = 0; i < f.getStatements().size(); i++) {
                HashMap<String, Integer> knownRegisterValuesCopy = new HashMap<>();
                knownRegisterValuesCopy.putAll(knownRegisterValues);
                previousKnownRegisterValues.put(i, knownRegisterValuesCopy);

                // reset known register values after each label:
                if (f.getStatements().get(i).label != null ||
                    f.getStatements().get(i).include != null) knownRegisterValues.clear();
                
                try {
                    if (optimizeStartingFromLine(f, i, knownRegisterValues, code, r)) {
                        i = Math.max(-1, i-2);   // go back a couple of statements, as more optimizations might chain
                        code.resetAddresses();
                        registersUsedAfter_previous = null;
                        
                        // Reset known values after optimization just in case:
                        knownRegisterValues.clear();
                        knownRegisterValues.putAll(previousKnownRegisterValues.get(i+1));
                    } else {
                        updateKnownRegisterValues(f.getStatements().get(i), knownRegisterValues, code);
                    }
                } catch(Exception e) {
                    config.error("Error encountered while running the SearchBasedOptimizer starting at line: " + f.getStatements().get(i));
                    config.error("Exception: " + e.getMessage());
                    config.error(Arrays.toString(e.getStackTrace()));
                }
                // DEBUGGING BLOCK:
//                Integer noptimizations = r.optimizerSpecificStats.get(SBO_RESULT_KEY);
//                if (MAX_NUMBER_OF_OPTIMIZATIONS >=0 && noptimizations != null &&
//                    noptimizations >= MAX_NUMBER_OF_OPTIMIZATIONS) {
//                    break;
//                }
            }
            // DEBUGGING BLOCK:
//            Integer noptimizations = r.optimizerSpecificStats.get(SBO_RESULT_KEY);
//            if (MAX_NUMBER_OF_OPTIMIZATIONS >=0 && noptimizations != null &&
//                noptimizations >= MAX_NUMBER_OF_OPTIMIZATIONS) {
//                break;
//            }
        }
        
        code.resetAddresses();

        Integer noptimizations = r.optimizerSpecificStats.get(SBO_RESULT_KEY);
        if (noptimizations == null) noptimizations = 0;
        config.info("SearchBasedOptimizer: "+noptimizations+" optimizations applied, " +
                    r.bytesSaved+" bytes, " + 
                    r.timeSavingsString() + " " +config.timeUnit+"s saved.");
        config.optimizerStats.addSavings(r);
        if (config.dialectParser != null) return config.dialectParser.postCodeModificationActions(code);
        return true;
    }
    
    
    private boolean isOptimizationSafeConstant(Expression exp, CodeBase code) 
    {
        if (exp.isConstant()) return true;
        if (exp.symbolName != null) {
            SourceConstant sc = code.getSymbol(exp.symbolName);
            if (sc != null) {
                if (sc.exp != null && sc.exp.isConstant()) return true;
                if (sc.isLabel()) {
                    CodeStatement s = sc.definingStatement;
                    s = s.source.getPreviousStatementTo(s, code);
                    while(s != null) {
                        if (s.op != null) return false;
                        if (s.org != null) {
                            return s.org.isConstant();
                        }
                        if (s.space != null && !s.space.isConstant()) return false;
                        if (s.include != null) return false;
                        s = s.source.getPreviousStatementTo(s, code);
                    }
                    if (s == null) return false;
                }
            }
        }
        return false;
    }
    
    
    private void updateKnownRegisterValues(CodeStatement s, HashMap<String, Integer> knownRegisterValues, CodeBase code)
    {
        if (s.op == null) return;
        
        // Clear all the output registers:
        List<String> toClear = new ArrayList<>();
        for(String reg2:knownRegisterValues.keySet()) {
            for(CPUOpDependency dep:s.op.getOutputDependencies()) {
                if (dep.register != null) {
                    if (CPUOpDependency.registerMatch(dep.register, reg2)) {
                        toClear.add(reg2);
                        break;
                    }
                }
            }
        }
        
        // "ld" instructions:
        if (s.op.isLd()) {
            if (s.op.args.get(0).isRegister() &&
                s.op.args.get(1).evaluatesToIntegerConstant() &&
                isOptimizationSafeConstant(s.op.args.get(1), code)) {
                Integer val = s.op.args.get(1).evaluateToInteger(s, code, true, null);
                if (val != null) {
                    assignKnownRegisterName(s.op.args.get(0).registerOrFlagName.toUpperCase(), val, knownRegisterValues, toClear);
                }
            } else if (s.op.args.get(0).isRegister() &&
                       s.op.args.get(1).isRegister()) {
                Integer val = knownRegisterValues.get(s.op.args.get(1).registerOrFlagName.toUpperCase());
                if (val != null) {
                    assignKnownRegisterName(s.op.args.get(0).registerOrFlagName.toUpperCase(), val, knownRegisterValues, toClear);
                }
            }
        }
        
        // "ldir / lddr / otir / otdr":
        if (s.op.spec.opName.equalsIgnoreCase("ldir") ||
            s.op.spec.opName.equalsIgnoreCase("lddr") ||
            s.op.spec.opName.equalsIgnoreCase("otir") ||
            s.op.spec.opName.equalsIgnoreCase("otdr")) {
            assignKnownRegisterName("BC", 0, knownRegisterValues, toClear);
        }
        
        // inc/dec when we had a known value:
        if (s.op.spec.opName.equalsIgnoreCase("inc") &&
            s.op.args.get(0).isRegister()) {
            Integer val = getKnownRegisterValue(s.op.args.get(0).registerOrFlagName.toUpperCase(), knownRegisterValues);
            if (val != null) {
                assignKnownRegisterName(s.op.args.get(0).registerOrFlagName.toUpperCase(), val+1, knownRegisterValues, toClear);
            }
        }
        if (s.op.spec.opName.equalsIgnoreCase("dec") &&
            s.op.args.get(0).isRegister()) {
            Integer val = getKnownRegisterValue(s.op.args.get(0).registerOrFlagName.toUpperCase(), knownRegisterValues);
            if (val != null) {
                assignKnownRegisterName(s.op.args.get(0).registerOrFlagName.toUpperCase(), val-1, knownRegisterValues, toClear);
            }
        }

        // Clear known vlaues after calls/jumps for safety:
        if (s.op.spec.isCall) {
            knownRegisterValues.clear();
            return;
        }
        if (s.op.spec.isJump && !s.op.spec.isConditional) {
            knownRegisterValues.clear();
            return;
        }
                
        for(String reg:toClear) {
            knownRegisterValues.remove(reg);
        }
        
//        config.debug(knownRegisterValues + "\t\tafter " + s.op);
    }
    
    
    private Integer getKnownRegisterValue(String reg, HashMap<String, Integer> knownRegisterValues)
    {
        if (knownRegisterValues.containsKey(reg)) return knownRegisterValues.get(reg);

        // See if it's a 16 bit register for which we know both sides:
        String primitiveRegs[] = CodeBase.get8bitRegistersOfRegisterPair(reg);
        if (primitiveRegs != null && primitiveRegs.length == 2) {
            if (knownRegisterValues.containsKey(primitiveRegs[0]) &&
                knownRegisterValues.containsKey(primitiveRegs[1])) {
                return knownRegisterValues.get(primitiveRegs[0])*256 + knownRegisterValues.get(primitiveRegs[1]);
            }
        }
        return null;
    }

    
    private void assignKnownRegisterName(String reg, int val, HashMap<String, Integer> knownRegisterValues, List<String> toClear)
    {
        String primitiveRegs[] = CodeBase.get8bitRegistersOfRegisterPair(reg);
        if (primitiveRegs != null && primitiveRegs.length == 2) {
            toClear.remove(primitiveRegs[0]);
            toClear.remove(primitiveRegs[1]);
            knownRegisterValues.put(primitiveRegs[0], val/256);
            knownRegisterValues.put(primitiveRegs[1], val%256);
        } else {
            toClear.remove(reg);
            knownRegisterValues.put(reg, val);
        }
    }
    
    
    private boolean preventOptimization(CodeStatement s, CodeBase code)
    {
        if (s.type != CodeStatement.STATEMENT_CPUOP &&
            s.type != CodeStatement.STATEMENT_NONE) return false;
        if (code.protectedFromOptimization(s)) {
            config.debug("SBO: preventOptimization: protected!");
            return true;
        }
        
        switch(s.op.spec.getName()) {
            case "nop":
            case "jr":
            case "jp":
            case "call":
            case "rst":
            case "ret":
            case "reti":
            case "retn":
            case "djnz":
            case "push":
            case "pop":
            case "di":
            case "ei":
            case "ldi":
            case "ldd":
            case "ldir":
            case "lddr":
            case "outi":
            case "outd":
            case "otir":
            case "otdr":
            case "halt":
            case "exx":
                config.debug("SBO: preventOptimization: unsupported instruction " + s.op.spec.getName());
                return true;
            case "ex":
                if (s.op.args.get(0).registerOrFlagName != null &&
                    s.op.args.get(0).registerOrFlagName.equals("af")) {
                    config.debug("SBO: preventOptimization: unsupported case of ex");
                    return true;
                }
        }
        
        // If a value is being assigned to "R", we do not try to optimize that, since
        // the optimizer will try to remove that assignment, which is usually wrong:
        if (s.op.isLd() && s.op.args.get(0).registerOrFlagName != null &&
            s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("r")) {
            return true;
        }
        
        for(int i = 0;i<s.op.spec.args.size();i++) {
            /* if (s.op.spec.args.get(i).wordConstantIndirectionAllowed ||
                s.op.spec.args.get(i).regIndirection != null ||
                s.op.spec.args.get(i).regOffsetIndirection != null) {
                // Only allow memory accesses for some instructions for now:
                if (!s.op.isLd()) {
                    config.debug("SBO: preventOptimization: unsupported indirection: " + s.op);
                    return true;
                }
            } else */ if (s.op.spec.args.get(i).byteConstantIndirectionAllowed) {
                config.debug("SBO: preventOptimization: unsupported op: " + s.op);
                return true;
            }
            if (s.op.args.get(i).registerOrFlagName != null &&
                s.op.args.get(i).registerOrFlagName.equalsIgnoreCase("sp")) {
                config.debug("SBO: preventOptimization: unsupported op: " + s.op);
                return true;
            }
        }
        
        return false;
    }
    
    
    private boolean optimizeStartingFromLine(SourceFile f, int line, HashMap<String, Integer> knownRegisterValues, CodeBase code, OptimizationResult r) throws Exception
    {
        if (f.getStatements().get(line).op == null) {
            registersUsedAfter_previous = null;
            return false;
        }
        config.debug(f.getStatements().get(line).fileNameLineString());
                
        List<CodeStatement> codeToOptimize = new ArrayList<>();
        int line2 = line;
        int codeToOptimizeSize = 0;
        int codeToOptimizeTime[] = new int[]{0, 0};
        while(codeToOptimize.size() < optimization_max_block_size && line2 < f.getStatements().size()) {
            CodeStatement s = f.getStatements().get(line2);
            if (!codeToOptimize.isEmpty() && s.label != null) {
                registersUsedAfter_previous = null;
                config.debug("SBO: skipping optimization since we found a label.");
                return false;
            }
            if (s.op != null) {
                if (preventOptimization(s, code)) {
                    registersUsedAfter_previous = null;
                    config.debug("SBO: skipping optimization since we found a yet unsupported case.");
                    return false;
                }
                codeToOptimize.add(s);
                codeToOptimizeSize += s.op.spec.sizeInBytes;
                codeToOptimizeTime[0] += s.op.spec.times[0];
                if (s.op.spec.times.length>=2) {
                    codeToOptimizeTime[1] += s.op.spec.times[1];
                } else {
                    codeToOptimizeTime[1] += s.op.spec.times[0];
                }
            }
            line2++;
        }
        
        config.debug("SBO: Optimizing from line " + f.getStatements().get(line).op + "\t\tknowing: " + knownRegisterValues);
//        config.info("SBO: Optimizing from line " + f.getStatements().get(line).op + "\t\tknowing: " + knownRegisterValues);
        
//        config.debug("SBO: Optimizing lines " + line + " -> " + line2);
//        for(CodeStatement s:codeToOptimize) {
//            config.debug("    " + s);
//        }
//        
        // - Create a specification, and synthesize test cases:
        Specification spec = new Specification();
        if (flags_nChecks != -1) spec.numberOfRandomSolutionChecks = flags_nChecks;
        if (flags_searchType != -1) {
            spec.searchType = flags_searchType;
        }
        if (spec.searchType == SEARCH_ID_OPS) {
            spec.maxOps = optimization_max_block_size;
        } else if (spec.searchType == SEARCH_ID_BYTES) {
            spec.maxOps = optimization_max_block_size + 1;
            spec.maxSizeInBytes = codeToOptimizeSize;
        } else {
            spec.maxOps = optimization_max_block_size + 1;
            spec.maxSimulationTime = Math.max(codeToOptimizeTime[0], codeToOptimizeTime[1]);
        }
        spec.allowedOps.remove("jp");
        spec.allowedOps.remove("jr");
        spec.allowedOps.remove("djnz");
        
        // - Find which registers we can use during search:
        List<RegisterNames> modifiedRegisters = findModifiedRegisters(codeToOptimize, f, code);
        List<RegisterNames> inputRegisters = findUsedRegisters(codeToOptimize, f, code);
        List<RegisterNames> registersUsedAfter = findRegistersUsedAfter(codeToOptimize, f, code, spec.allowGhostRegisters, modifiedRegisters, inputRegisters);
        boolean goalRequiresSettingMemory = false;
        registersUsedAfter_previous = registersUsedAfter;
        List<RegisterNames> registersNotUsedAfter = findRegistersNotUsedAfter(registersUsedAfter, f, code, spec.allowGhostRegisters);
        for(RegisterNames reg:modifiedRegisters) {
            String regName = CPUConstants.registerName(reg);
            if (!spec.allowedRegisters.contains(regName)) spec.allowedRegisters.add(regName);
        }
        for(RegisterNames reg:registersNotUsedAfter) {
            String regName = CPUConstants.registerName(reg);
            if (!spec.allowedRegisters.contains(regName)) spec.allowedRegisters.add(regName);
        }
        for(RegisterNames reg:inputRegisters) {
            String regName = CPUConstants.registerName(reg);
            if (!spec.allowedRegisters.contains(regName)) spec.allowedRegisters.add(regName);
        }
//        System.out.println("    - Allowed Registers: " + spec.allowedRegisters);
                
        // - Find the set of constants used in the code:
        spec.allowed8bitConstants.clear();
        spec.allowed16bitConstants.clear();
        HashMap<Integer, List<Expression>> constantsToExpressions = new HashMap<>();
        for(CodeStatement s:codeToOptimize) {
            for(int i = 0;i<s.op.args.size();i++) {
                Expression arg = s.op.args.get(i);
                
                // Memory writes:
                if (i == 0 && s.op.writesToMemory()) {
                    spec.allowRamUse = true;
                    goalRequiresSettingMemory = true;
                }
                // Memory reads:
                if (i == 0 && s.op.readsFromMemory()) {
                    spec.allowRamUse = true;
                }                
                
                if (arg.evaluatesToIntegerConstant()) {
                    Integer value = arg.evaluateToInteger(s, code, true);
                    if (value == null) {
                        config.warn("Could not evaluate " + arg + " to an integer constant");
                        return false;
                    }
                    if (s.op.spec.args.get(i).byteConstantAllowed ||
                        s.op.spec.args.get(i).wordConstantAllowed) {
                        if (!spec.allowed8bitConstants.contains(value)) spec.allowed8bitConstants.add(value);
                        if (!spec.allowed16bitConstants.contains(value)) spec.allowed16bitConstants.add(value);
                        if (constantsToExpressions.containsKey(value)) {
                            constantsToExpressions.get(value).add(arg);
                        } else {
                            List<Expression> l = new ArrayList<>();
                            l.add(arg);
                            constantsToExpressions.put(value, l);
                        }
                    }
                    if (s.op.isLd() && 
                        s.op.spec.args.get(i).wordConstantIndirectionAllowed) {
                        if (!spec.allowed16bitConstants.contains(value)) {
                            spec.allowed16bitConstants.add(value);
                        }
                        if (constantsToExpressions.containsKey(value)) {
                            constantsToExpressions.get(value).add(arg.args.get(0));
                        } else {
                            List<Expression> l = new ArrayList<>();
                            l.add(arg.args.get(0));
                            constantsToExpressions.put(value, l);
                        }
                    }
                }
            }
        }
        // to do: find offset constants (and support memory access)
//        System.out.println("    - Allowed 8bit Constants: " + spec.allowed8bitConstants);
//        System.out.println("    - Allowed 16bit Constants: " + spec.allowed16bitConstants);
                
        // Find which registers and flags are used afterwards:
        registersUsedAfter.remove(RegisterNames.R);
        if (!inputRegisters.contains(RegisterNames.F)) inputRegisters.add(RegisterNames.F);
        List<Integer> flagsUsedAfter = findFlagsUsedAfter(codeToOptimize, f, code);
//        System.out.println("    - Input registers: " + inputRegisters);
//        System.out.println("    - Goal registers: " + registersUsedAfter);
//        System.out.println("    - Goal flags: " + flagsUsedAfter);

        // Start State:
        for(String reg:knownRegisterValues.keySet()) {
            SpecificationExpression exp = new SpecificationExpression();
            exp.leftRegister = CPUConstants.registerByName(reg);
            exp.right = Expression.constantExpression(knownRegisterValues.get(reg), config);
            spec.startState.add(exp);
        }
        
        // Precompute goalDependencies / initialDependencies:
        List<CPUOpDependency> allDependencies = spec.precomputeAllDependencies();
        spec.goalDependencies = new boolean[allDependencies.size()];
        spec.initialDependencies = new boolean[allDependencies.size()];
        for(int i = 0;i<allDependencies.size();i++) {
            spec.initialDependencies[i] = true;
            CPUOpDependency dep = allDependencies.get(i);
            if (dep.register != null && registersUsedAfter.contains(CPUConstants.registerByName(dep.register))) {
                spec.goalDependencies[i] = true;
            } else if (dep.flag != null && flagsUsedAfter.contains(CPUConstants.flagByName(dep.flag))) {
                spec.goalDependencies[i] = true;
            } else if (dep.memoryStart != null && goalRequiresSettingMemory) {
                spec.goalDependencies[i] = true;
            } else {
                spec.goalDependencies[i] = false;
            }
//            System.out.println("  goalDependency: " + allDependencies.get(i));
        }
        spec.precomputeGoalDependencyIndexes();

        // Precompute test cases:
        spec.codeStartAddress = codeToOptimize.get(0).getAddress(code);
        IMemory z80Memory;
        if (spec.allowRamUse) {
            z80Memory = new TrackingZ80Memory(null);
        } else {
            z80Memory = new PlainZ80Memory();
        }
        Z80Core z80 = new Z80Core(z80Memory, new PlainZ80IO(), new CPUConfig(config));        
        if (spec.allowRamUse) ((TrackingZ80Memory)z80Memory).setCPU(z80);
        for(int i = 0;i<PlainZ80Memory.MEMORY_SIZE;i++) {
            int v = random.nextInt(256);
            z80Memory.writeByte(i, v);
        }
        if (spec.allowRamUse) {
//            System.out.println("value at 52432: " + z80Memory.readByte(52432));
//            System.out.println("value at 52433: " + z80Memory.readByte(52433));
            ((TrackingZ80Memory)z80Memory).clearMemoryAccesses();
        }
        spec.precomputedTestCases = new PrecomputedTestCase[spec.numberOfRandomSolutionChecks];
        spec.testCaseGenerator = new PrecomputedTestCaseGeneratorForOptimization(
                spec, codeToOptimize, inputRegisters, registersUsedAfter,
                flagsUsedAfter, z80, z80Memory, code, config);
        for(int i = 0;i<spec.numberOfRandomSolutionChecks;i++) {
            spec.precomputedTestCases[i] = spec.testCaseGenerator.generateTestCase(config);
        }
        
        // Search:
        SequenceFilter filter = new SequenceFilter(internalConfig);
        filter.setSpecification(spec);
        try {
            filter.loadEquivalences("data/equivalencies-l1.txt");
        } catch(Exception e) {
            config.error("Could not load equivalences files...: " + e.getMessage());
            config.error(Arrays.toString(e.getStackTrace()));
            return false;
        }
        SourceFile sf = new SourceFile(f.fileName + "[optimized]", null, null, code, config);
        if (!workGenerate(spec, filter, allDependencies, sf, code)) {
            return false;
        }
        
        // Replace constants by their corresponding expressions:
//        System.out.println("constantsToExpressions: " + constantsToExpressions);
        for(CodeStatement s:sf.getStatements()) {
            if (s.op != null) {
                for(int i = 0;i<s.op.args.size();i++) {
                    Expression arg = s.op.args.get(i);
                    if (arg.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                        List<Expression> l = constantsToExpressions.get(arg.integerConstant);
                        if (l != null && !l.isEmpty()) {
                            s.op.args.set(i, l.get(0));
                        }
                    } else if (arg.type == Expression.EXPRESSION_PARENTHESIS &&
                               arg.args.size() == 1 &&
                               arg.args.get(0).type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                        // indirection:
                        List<Expression> l = constantsToExpressions.get(arg.args.get(0).integerConstant);
                        if (l != null && !l.isEmpty()) {
                            arg.args.set(0, l.get(0));
                        }
                    }
                }
            }
        }
                
        // - If better, replace:
        return replaceIfBetter(codeToOptimize, sf.getStatements(), spec.searchType, f, r);
    }
    
    
    boolean replaceIfBetter(List<CodeStatement> originalCode, List<CodeStatement> optimized, int criteria, SourceFile f, 
                            OptimizationResult r)
    {
        boolean better = false;
        int n1 = 0, bytes1 = 0, time1[] = {0, 0};
        int n2 = 0, bytes2 = 0, time2[] = {0, 0};

        for(CodeStatement s:originalCode) {
            if (s.op != null) {
                n1++;
                bytes1 += s.op.spec.sizeInBytes;
                time1[0] += s.op.spec.times[0];
                if (s.op.spec.times.length >= 2) {
                    time1[1] += s.op.spec.times[1];
                } else {
                    time1[1] += s.op.spec.times[0];
                }
            }
        }

        for(CodeStatement s:optimized) {
            if (s.op != null) {
                n2++;
                bytes2 += s.op.spec.sizeInBytes;
                time2[0] += s.op.spec.times[0];
                if (s.op.spec.times.length >= 2) {
                    time2[1] += s.op.spec.times[1];
                } else {
                    time2[1] += s.op.spec.times[0];
                }
            }
        }
        
        boolean betterTime = (time2[0] < time1[0] || time2[1] < time1[1]) &&
                             (time2[0] <= time1[0] && time2[1] <= time1[1]);
        
        if (criteria == SEARCH_ID_OPS) {
            if (n2 < n1 ||
                (n2 == n1 && 
                 (bytes2 < bytes1 || betterTime) &&
                 (bytes2 <= bytes1 && time2[0] <= time1[0] && time2[1] <= time1[1]))) {
                better = true;
            }
        } else if (criteria == SEARCH_ID_BYTES) {
            if (bytes2 < bytes1 ||
                (bytes2 == bytes1 && betterTime)) {
                better = true;
            }
        } else if (criteria == SEARCH_ID_CYCLES) {
            if (betterTime ||
                (time2[0] == time1[0] && time2[1] == time1[1] && bytes2 < bytes1)) {
                better = true;
            }
        }

        if (better) {
            int bytesSaved = bytes1 - bytes2;
            int timeSaved[] = new int[]{time1[0] - time2[0], time1[1] - time2[1]};
            String timeSavedString = timeSaved[0] + "/" + timeSaved[1];
            if (timeSaved[0] == timeSaved[1]) {
                timeSavedString = "" + timeSaved[0];
            }
            String originalString = "";
            String optimizedString = "";
            for(CodeStatement s:originalCode) {
                originalString += s.op.toString();
                if (s != originalCode.get(originalCode.size()-1)) {
                    originalString += "; ";
                }
            }
            for(CodeStatement s:optimized) {
                optimizedString += s.op.toString();
                if (s != optimized.get(optimized.size()-1)) {
                    optimizedString += "; ";
                }
            }
            config.info("Search-based optimization", originalCode.get(0).fileNameLineString(),
                        "replace " + originalString + " with " + optimizedString + " ("+bytesSaved+" bytes, " +
                        timeSavedString + " " +config.timeUnit+"s saved)");
            // replace!:
            int insertionPoint = f.getStatements().indexOf(originalCode.get(0));
            for(CodeStatement s:originalCode) {
                f.getStatements().remove(s);
            }
            for(CodeStatement s:optimized) {
                s.source = f;
                s.sl.lineNumber = originalCode.get(0).sl.lineNumber;
                f.addStatement(insertionPoint, s);
                insertionPoint++;
            }
            
            r.addOptimizerSpecific(SBO_RESULT_KEY, 1);
            r.addSavings(bytesSaved, timeSaved);
        }
        
        return better;
    }
    
    
    List<RegisterNames> findUsedRegisters(List<CodeStatement> l, SourceFile f, CodeBase code)
    {
        List<RegisterNames> registers = new ArrayList<>();
        
        for(RegisterNames reg:CPUConstants.eightBitRegisters) {
            String regName = CPUConstants.registerName(reg);
            CPUOpDependency dep = new CPUOpDependency(regName.toUpperCase(), null, null, null, null);        
            for(CodeStatement s:l) {
                if (!Pattern.regNotUsed(s, regName, f, code)) {
                    registers.add(reg);
                    break;
                }
                if (s.op != null && s.op.checkOutputDependency(dep) == null) {
                    // register written by this instruction, so, it can't be an input register
                    break;
                }
            }
        }
        
        return registers;
    }

    
    List<RegisterNames> findModifiedRegisters(List<CodeStatement> l, SourceFile f, CodeBase code)
    {
        List<RegisterNames> registers = new ArrayList<>();
        
        for(RegisterNames reg:CPUConstants.allRegisters) {
            for(CodeStatement s:l) {
                if (!Pattern.regNotModified(s, CPUConstants.registerName(reg), f, code)) {
                    registers.add(reg);
                    break;
                }
            }
        }
        
        return registers;
    }


    List<RegisterNames> findRegistersUsedAfter(List<CodeStatement> l, SourceFile f, CodeBase code, boolean allowGhostRegisters,
                                               List<RegisterNames> modifiedRegisters, List<RegisterNames> inputRegisters)
    {
        List<RegisterNames> registers = new ArrayList<>();
     
        if (registersUsedAfter_previous == null) {        
            // recompute from scratch (this can be slow some times):
            for(RegisterNames reg:CPUConstants.eightBitRegisters) {
                if (!allowGhostRegisters && CPUConstants.isGhostRegister(reg)) continue;
                if (reg == RegisterNames.R) continue;
                CodeStatement s = l.get(l.size()-1);
                Boolean notUsed = Pattern.regNotUsedAfter(s, CPUConstants.registerName(reg), f, code);
                if (notUsed == null || notUsed == false) {
                    registers.add(reg);
                }
            }
        } else {
            // use cache:
//            System.out.println("cache");
            for(RegisterNames reg:CPUConstants.eightBitRegisters) {
                if (!allowGhostRegisters && CPUConstants.isGhostRegister(reg)) continue;
                if (reg == RegisterNames.R) continue;
                boolean check = false, used = false;
                if (modifiedRegisters.contains(reg)) {
                    // - If a register is in "modifiedRegisters" we need to calculate from scratch again
                    check = true;
                }
                if (registersUsedAfter_previous.contains(reg)) {
                    if (inputRegisters.contains(reg)) {
                        check = true;
                    } else {
                        // - If a register was used, but is not in "inputRegisters", then it's still used (assert that it cannot be in "modifiedRegisters")
                        used = true;
                    }
                }
                if (check) {
                    CodeStatement s = l.get(l.size()-1);
                    Boolean notUsed = Pattern.regNotUsedAfter(s, CPUConstants.registerName(reg), f, code);
                    if (notUsed == null || notUsed == false) used = true;
//                } else {
//                    // DEBUG (to ensure using the cache is identical to calculating from scratch):
//                    CodeStatement s = l.get(l.size()-1);
//                    Boolean notUsed = Pattern.regNotUsedAfter(s, CPUConstants.registerName(reg), f, code);
//                    if (notUsed == null || notUsed == false) {
//                        if (!used) {
//                            System.out.println("ERROR: "+reg+" is used!");
//                            System.out.println("registersUsedAfter_previous: " + registersUsedAfter_previous);
//                            System.out.println("modifiedRegisters: " + modifiedRegisters);
//                            System.out.println("inputRegisters: " + inputRegisters);
//                            System.out.println("l: " + l);
//                        }
//                    } else {
//                        if (used) System.out.println("ERROR: it is not used!");
//                    }
                }
                if (used) registers.add(reg);
            }
        }
                
        return registers;
    }
    

    List<RegisterNames> findRegistersNotUsedAfter(List<RegisterNames> registersUsedAfter, SourceFile f, CodeBase code, boolean allowGhostRegisters)
    {
        List<RegisterNames> registers = new ArrayList<>();
        
        for(RegisterNames reg:CPUConstants.allRegisters) {
            if (!allowGhostRegisters && CPUConstants.isGhostRegister(reg)) continue;
            if (!registersUsedAfter.contains(reg)) {                
                registers.add(reg);
                break;
            }
        }        
        
        return registers;
    }    

    
    List<Integer> findFlagsUsedAfter(List<CodeStatement> l, SourceFile f, CodeBase code)
    {
        List<Integer> flags = new ArrayList<>();
        
        for(Integer flag:new int[]{ CPUConstants.flag_C, CPUConstants.flag_N, 
                                    CPUConstants.flag_PV, CPUConstants.flag_H, 
                                    CPUConstants.flag_Z, CPUConstants.flag_S}) {
            for(CodeStatement s:l) {
                Boolean notUsed = Pattern.flagNotUsedAfter(s, CPUConstants.flagName(flag), f, code);
                if (notUsed == null || notUsed == false) {
                    flags.add(flag);
                    break;
                }
            }
        }
        
        
        return flags;
    }
}

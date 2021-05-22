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
import code.SourceFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import parser.SourceLine;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import workers.MDLWorker;

/**
 *
 * @author santi
 */
public class SearchBasedOptimizer implements MDLWorker {    
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
    
    // randomize the register contents:
    public RegisterNames eightBitRegistersToRandomize[] = null;

    MDLConfig config;
    boolean trigger = false;
    int operation = SBO_GENERATE;
    boolean showNewBestDuringSearch = true;
    
    // Search configuration parameters if specified via flags (will overwrite
    // those in the Specification file):
    int flags_searchType = -1;
    int flags_codeStartAddress = -1;
    int flags_maxSimulationTime = -1;
    int flags_maxSizeInBytes = -1;
    int flags_maxOps = -1;
    int flags_nThreads = -1;
    
    int optimization_max_block_size = 2;
    
        
    
    public SearchBasedOptimizer(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    @Override
    public String docString() {
        return "- ```-so```: Runs the search-based-based optimizer (if the input file is an assembler file (.asm/.z80/.a80), it'll try to optimize it; if the input file is a specification file (.txt), it will use as a target for program generation; which of the two will be auto-detected based on the file extension). You can pass an optional parameter: ````-so size```, ```-so speed```, or ```-so ops```, to tell the optimizer to optimize for program size, execution speed, or number of instructions. This will overwrite whatever is specified in the specificaiton file (default is to optimize by number of ops).\n" +
               "- ```-so-gen```: Like above, but instead of autodetecting, it always assumes the input file is a specification file for program generation.\n" +
               "- ```-so-opt```: Like above, but instead of autodetecting, it always assumes the input file is an assembler file for optimization.\n" +
               "- ```-so-maxops <n>```: (only for program generation) Sets the upper limit of how many CPU ops the resulting program can have.\n" +
               "- ```-so-maxsize <n>```: (only for program generation) Sets the maximum number of bytes the resulting program can occupy.\n" +
               "- ```-so-maxtime <n>```: (only for program generation) Sets the maximum time (in whichever units the target CPU uses) that the resulting program can take to execute.\n" +
               "- ```-so-threads <n>```: Sets the number of threads to use during search (default value is the number of cores of the CPU).\n";
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
                       config.inputFile.endsWith(".asm") ||
                       config.inputFile.endsWith(".z80") ||
                       config.inputFile.endsWith(".a80")) {
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

        SourceFile sf = new SourceFile("autogenerated.asm", null, null, code, config);
        code.addOutput(null, sf, 0);

        if (!spec.precomputeTestCases(code, config)) {
            config.error("Unable to precompute test cases");
            return false;
        }
        
        // Precompute all the op dependencies before search:
        List<CPUOpDependency> allDependencies = spec.precomputeAllDependencies();
        int nDependencies = allDependencies.size();
        config.debug("nDependencies: " + nDependencies);
        
        SequenceFilter filter = new SequenceFilter(config);
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
        
        List<SBOCandidate> allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, 2, config);
        if (allCandidateOps == null) return false;
                
        // Precalculate which registers to randomize:
        {
            List<RegisterNames> toRandomize = new ArrayList<>();
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
                }
            }
            // Remove the ones that will be initialized already in the start state:
            for(SpecificationExpression exp:spec.startState) {
                if (exp.leftRegister != null) {
                    for(RegisterNames reg:CPUConstants.primitive8BitRegistersOf(exp.leftRegister)) {
                        toRandomize.remove(reg);
                    }
                }
            }
            eightBitRegistersToRandomize = new RegisterNames[toRandomize.size()];
            for(int i = 0;i<toRandomize.size();i++) {
                eightBitRegistersToRandomize[i] = toRandomize.get(i);
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
                    if (depth == 4) {
                        // Increase precomputation (not worth it before this point):
                        allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, 3, config);
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
                                            showNewBestDuringSearch, code, config,
                                            spec.searchType, depth, codeMaxAddress, spec.maxSimulationTime);
                        threads[i].start();
                    }
                    for(int i = 0;i<nThreads;i++) threads[i].join();
                    if (state.bestOps != null) break;
                    String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
                    config.info("SearchBasedOptimizer: depth "+depth+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                }

            } else if (spec.searchType == SEARCH_ID_BYTES) {
                for(int size = 0; size<=spec.maxSizeInBytes; size++) {
                    if (size == 4) {
                        // Increase precomputation (not worth it before this point):
                        allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, 3, config);
                        if (allCandidateOps == null) return false;                        
                    }
                    state.init(allCandidateOps, size==0);
                    for(int i = 0;i<nThreads;i++) {
                        threads[i] = new SBOExecutionThread("thread-" + i, 
                                            spec, allDependencies, goalDependencies,
                                            state, eightBitRegistersToRandomize,
                                            showNewBestDuringSearch, code, config,
                                            spec.searchType, spec.maxOps, spec.codeStartAddress + size, spec.maxSimulationTime);
                        threads[i].start();
                    }
                    for(int i = 0;i<nThreads;i++) threads[i].join();
                    if (state.bestOps != null) break;
                    String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
                    config.info("SearchBasedOptimizer: size "+size+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                }

            } else if (spec.searchType == SEARCH_ID_CYCLES) {
                for(int maxTime = 0; maxTime<=spec.maxSimulationTime; maxTime++) {
                    if (maxTime == nopDuration*4) {
                        // Increase precomputation (not worth it before this point):
                        allCandidateOps = SBOCandidate.precomputeCandidateOps(spec, allDependencies, code, filter, 3, config);
                        if (allCandidateOps == null) return false;                        
                    }
                    state.init(allCandidateOps, maxTime==0);
                    for(int i = 0;i<nThreads;i++) {
                        threads[i] = new SBOExecutionThread("thread-" + i, 
                                            spec, allDependencies, goalDependencies,
                                            state, eightBitRegistersToRandomize,
                                            showNewBestDuringSearch, code, config,
                                            spec.searchType, spec.maxOps, spec.codeStartAddress + spec.maxSizeInBytes, maxTime);
                        threads[i].start();
                    }
                    for(int i = 0;i<nThreads;i++) threads[i].join();
                    if (state.bestOps != null) break;
                    String time = String.format("%.02f", (System.currentTimeMillis() - start)/1000.0f) + "s";
                    config.info("SearchBasedOptimizer: time "+maxTime+" complete ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
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
            config.error("No program that satisfied the specification was found.");
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
        config.info("SearchBasedOptimizer: search ended ("+state.solutionsEvaluated+" solutions tested, time elapsed: "+time+")");
                
        return true;
    }
    
    
    private boolean workOptimize(CodeBase code) {
        OptimizationResult r = new OptimizationResult();
        
        for (SourceFile f : code.getSourceFiles()) {
            for (int i = 0; i < f.getStatements().size(); i++) {
                if (optimizeStartingFromLine(f, i, code, r)) {
                    i = Math.max(0, i-2);   // go back a couple of statements, as more optimizations might chain
                }
            }
        }
        
        code.resetAddresses();

        Integer noptimizations = r.optimizerSpecificStats.get("Search-based optimizer optimizations");
        if (noptimizations == null) noptimizations = 0;
        config.info("SearchBasedOptimizer: "+noptimizations+" optimizations applied, " +
                    r.bytesSaved+" bytes, " + 
                    r.timeSavingsString() + " " +config.timeUnit+"s saved.");
        config.optimizerStats.addSavings(r);
        if (config.dialectParser != null) return config.dialectParser.postCodeModificationActions(code);
        return true;
    }
    
    
    private boolean preventOptimization(CodeStatement s, CodeBase code)
    {
        if (code.protectedFromOptimization(s)) return true;
        
        switch(s.op.spec.getName()) {
            case "nop":
            case "jr":
            case "jp":
            case "call":
            case "rst":
            case "ret":
            case "reti":
            case "djnz":
            case "push":
            case "pop":
                return true;
        }
        
        for(int i = 0;i<s.op.spec.args.size();i++) {
            if (s.op.spec.args.get(i).byteConstantIndirectionAllowed ||
                s.op.spec.args.get(i).wordConstantIndirectionAllowed ||
                s.op.spec.args.get(i).regIndirection != null ||
                s.op.spec.args.get(i).regOffsetIndirection != null) {
                return true;
            }
        }
        
        return false;
    }
    
    
    private boolean optimizeStartingFromLine(SourceFile f, int line, CodeBase code, OptimizationResult r)
    {
        if (f.getStatements().get(line).op == null) return false;
        
        List<CodeStatement> codeToOptimize = new ArrayList<>();
        int line2 = line;
        while(codeToOptimize.size() < optimization_max_block_size && line2 < f.getStatements().size()) {
            CodeStatement s = f.getStatements().get(line2);
            if (s.op != null) {
                if (preventOptimization(s, code)) {
                    return false;
                }
                codeToOptimize.add(s);
            }
            line2++;
        }
        
//        System.out.println("optimizing lines " + line + " -> " + line2);
//        for(CodeStatement s:codeToOptimize) {
//            System.out.println("    " + s);
//        }
        
        return false;
    }
}

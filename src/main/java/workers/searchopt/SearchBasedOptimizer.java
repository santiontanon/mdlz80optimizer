/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpDependency;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import parser.SourceLine;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import workers.MDLWorker;
import workers.searchopt.Specification.PrecomputedTestCase;

/**
 *
 * @author santi
 */
public class SearchBasedOptimizer implements MDLWorker {    
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
    boolean showNewBestDuringSearch = true;
    
    // search configuration parameters if specified via flags (will overwrite
    // those in the Specification file):
    int flags_searchType = -1;
    int flags_codeStartAddress = -1;
    int flags_maxSimulationTime = -1;
    int flags_maxSizeInBytes = -1;
    int flags_maxOps = -1;
    int flags_nThreads = -1;
    
    CodeBase code = null;
    Specification spec = null;
    int nDependencies = 0;

    SBOGlobalSearchState state = null;
        
    
    public SearchBasedOptimizer(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    @Override
    public String docString() {
        return "- ```-so```: Runs the search-based-based optimizer (input file is a function specification instead of an assembler file).\n" + 
               "- ```-so-ops```/```-so-size```/```-so-time```: Runs the optimizer with a specific optimization goal (minimize the number of CPU ops, number of bytes, or execution time). This will overwrite whatever is specified in the specificaiton file (default is to optimize by number of ops).\n" +
               "- ```-so-maxops <n>```: Sets the upper limit of how many CPU ops the resulting program can have.\n" +
               "- ```-so-maxsize <n>```: Sets the maximum number of bytes the resulting program can occupy.\n" +
               "- ```-so-maxtime <n>```: Sets the maximum time (in whichever units the target CPU uses) that the resulting program can take to execute.\n" +
               "- ```-so-threads <n>```: Sets the number of threads to use during search (default value is the number of cores of the CPU).\n";
    }

    
    @Override
    public String simpleDocString() {
        return "- ```-so```: Runs the search-based-based optimizer (input file is a function specification instead of an assembler file).\n";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-so")) {
            flags.remove(0);
            trigger = true;
            config.codeSource = MDLConfig.CODE_FROM_SEARCHBASEDOPTIMIZER;
            return true;
        }
        if (flags.get(0).equals("-so-ops")) {
            flags.remove(0);
            trigger = true;
            flags_searchType = SEARCH_ID_OPS;
            config.codeSource = MDLConfig.CODE_FROM_SEARCHBASEDOPTIMIZER;
            return true;
        }
        if (flags.get(0).equals("-so-size")) {
            flags.remove(0);
            trigger = true;
            flags_searchType = SEARCH_ID_BYTES;
            config.codeSource = MDLConfig.CODE_FROM_SEARCHBASEDOPTIMIZER;
            return true;
        }
        if (flags.get(0).equals("-so-time")) {
            flags.remove(0);
            trigger = true;
            flags_searchType = SEARCH_ID_CYCLES;
            config.codeSource = MDLConfig.CODE_FROM_SEARCHBASEDOPTIMIZER;
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
        // Parse specification file:
        this.code = a_code;
        spec = SpecificationParser.parse(config.inputFile, code, config);
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
        List<CPUOpDependency> allDependencies = new ArrayList<>();
        for(String regName: new String[]{"A", "F", "B", "C", "D", "E", "H", "L", 
                                         "IXH", "IXL", "IYH", "IYL"}) {
            if (regName.equals("F") ||
                spec.allowedRegisters.contains(regName.toLowerCase())) {
                allDependencies.add(new CPUOpDependency(regName, null, null, null, null));
            }
        }
        for(String flagName: new String[]{"S" ,"Z" ,"H" , "P/V" ,"N" , "C"}) {
            // "H" and "N" are only useful for DAA:
            if (!spec.allowedOps.contains("daa") && flagName.equals("H")) continue;
            if (!spec.allowedOps.contains("daa") && flagName.equals("N")) continue;
            if (flagName.equals("P/V")) {
                if (!spec.allowedOps.contains("jp") &&
                    !spec.allowedOps.contains("jr") &&
                    !spec.allowedOps.contains("call")) {
                    continue;
                }
            }
            allDependencies.add(new CPUOpDependency(null, flagName, null, null, null));
        }
        if (spec.allowIO) {
            allDependencies.add(new CPUOpDependency(null, null, "C", null, null));
        }
        if (spec.allowRamUse) {
            allDependencies.add(new CPUOpDependency(null, null, null, "0", "0x10000"));
        }
        nDependencies = allDependencies.size();
        config.debug("nDependencies: " + nDependencies);
        
        List<SBOCandidate> allCandidateOps = precomputeCandidateOps(spec, allDependencies, code, 2);
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
                
        state = new SBOGlobalSearchState();
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
                        allCandidateOps = precomputeCandidateOps(spec, allDependencies, code, 3);
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
                        allCandidateOps = precomputeCandidateOps(spec, allDependencies, code, 3);
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
                        allCandidateOps = precomputeCandidateOps(spec, allDependencies, code, 3);
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
    
    
    List<SBOCandidate> precomputeCandidateOps(Specification spec, List<CPUOpDependency> allDependencies, CodeBase code,
            int maxLength)
    {
        List<SBOCandidate> candidates = new ArrayList<>();
        
        /*
        OK - AND/OR/XOR
        OK - INC/DEC
        OK - ADD/ADC/SUB/SBC
        OK - LD
        OK - RLCA/RLA/RRCA/RRA/RLC/RL/RRC/RR
        OK - SLA/SRA/SRL/SLI
        OK - CPL/NEG
        OK - BIT/SET/RES
        OK - CCF/SCF
        OK - CP
        OK - JP/JR/DJNZ
        - PUSH/POP
        - EX/EXX
        - LDI/LDD/LDIR/LDDR
        - CPI/CPD/CPIR/CPDR
        - DAA
        - HALT/DI/EI/IM
        - RLD/RRD
        - CALL/RET/RETI/RETN
        - RST
        - IN/INI/IND/INIR/INDR
        - OUT/OUTI/OUTD/OTIR/OTDR
        - NOP
        */
        
        if (spec.allowedOps.contains("and") ||
            spec.allowedOps.contains("or") ||
            spec.allowedOps.contains("xor") ||
            spec.allowedOps.contains("cp")) {
            if (!precomputeAndOrXorCp(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("inc") ||
            spec.allowedOps.contains("dec")) {
            if (!precomputeIncDec(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("add") ||
            spec.allowedOps.contains("adc") ||
            spec.allowedOps.contains("sub") ||
            spec.allowedOps.contains("sbc")) {
            if (!precomputeAddAdcSubSbc(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("ld")) {
            if (!precomputeLd(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("rlc") ||
            spec.allowedOps.contains("rl") ||
            spec.allowedOps.contains("rrc") ||
            spec.allowedOps.contains("rr") ||
            spec.allowedOps.contains("rlca") ||
            spec.allowedOps.contains("rla") ||
            spec.allowedOps.contains("rrca") ||
            spec.allowedOps.contains("rra")) {
            if (!precomputeRotations(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("sla") ||
            spec.allowedOps.contains("sra") ||
            spec.allowedOps.contains("srl") ||
            spec.allowedOps.contains("sli")) {
            if (!precomputeShifts(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("cpl") ||
            spec.allowedOps.contains("neg")) {
            if (!precomputeNegations(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("bit") ||
            spec.allowedOps.contains("set") ||
            spec.allowedOps.contains("res")) {
            if (!precomputeBits(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("ccf") ||
            spec.allowedOps.contains("sfc")) {
            if (!precomputeCarry(candidates, spec, allDependencies, code)) return null;
        }
        if (spec.allowedOps.contains("jp") ||
            spec.allowedOps.contains("jr") ||
            spec.allowedOps.contains("djnz")) {
            if (!precomputeJumps(candidates, spec, allDependencies, code)) return null;
        }
        
        config.debug("allCandidateOps: " + candidates.size());
        
        // Precalculate which instructions can contribute to the solution:
        boolean goalDependencies[] = spec.getGoalDependencies(allDependencies);
        spec.getGoalDependenciesSatisfiedFromTheStart(allDependencies); // precalculate for later
        for(int i = 0;i<nDependencies;i++) {
            if (!goalDependencies[i]) continue;
            boolean isVariable = false;
            if (allDependencies.get(i).register != null) {
                RegisterNames reg = CPUConstants.registerByName(allDependencies.get(i).register);
                Integer value = null;
                for(PrecomputedTestCase ptc:spec.precomputedTestCases) {
                    Integer value2 = ptc.getGoalRegisterValue(reg);
                    if (value2 == null) break;
                    if (value == null) {
                        value = value2;
                    } else {
                        if (!value.equals(value2)) {
                            isVariable = true;
                            break;
                        }
                    }
                }
            }
            for(SBOCandidate op:candidates) {
                if (op.outputDependencies[i] != 0) {
                    if (isVariable &&
                        op.op.spec.getName().equalsIgnoreCase("ld") &&
                        op.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                        // this output is variable, so, it will never be satisfied by a "ld XXX,constant"
                        continue;
                    }
                    op.directContributionToGoal = true;
                }
            }
        }
        
        // Precompute which ops can follow which other ops:
        HashMap<String, SBOCandidate> candidateOpsByPrefix = new HashMap<>();
        SBOCandidate.precomputeCandidates(candidateOpsByPrefix, 
                candidates, allDependencies, spec, code, maxLength, config);
        
        return candidates;
    }
    
    
    boolean precomputeOp(String line, List<SBOCandidate> candidates, List<CPUOpDependency> allDependencies, CodeBase code)
    {
        List<String> tokens = config.tokenizer.tokenize(line);
        SourceFile sf = new SourceFile("dummy", null, null, code, config);
        SourceLine sl = new SourceLine(line, sf, 0);
        List<CodeStatement> l = config.lineParser.parse(tokens, sl, sf, null, code, config);
        if (l == null || l.size() != 1) {
            config.error("Parsing candidate op in the search-based optimizer resulted in none, or more than one op! " + line);
            return false;
        }
        CodeStatement s = l.get(0);
        SBOCandidate candidate = new SBOCandidate(s, allDependencies, code, config);
        if (candidate.bytes == null) return false;
        candidates.add(candidate);
        return true;
    }
    
    
    boolean precomputeAndOrXorCp(List<SBOCandidate> candidates, Specification spec, 
                                 List<CPUOpDependency> allDependencies, CodeBase code)
    {
        if (!spec.allowedRegisters.contains("a")) return true;
        
        String opNames[] = {"and", "or", "xor", "cp"};
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String opName : opNames) {
            if (!spec.allowedOps.contains(opName)) continue;
                        
            // register argument:
            for(String regName : regNames) {
                if (!spec.allowedRegisters.contains(regName)) continue;
                
                if (opName.equals("and") && spec.allowedOps.contains("or") && !spec.allowedOps.contains("daa")) {
                    // If we don't have "DAA", the "H" flag is useless, and hence, "or a" and "and a" are equivalent
                    if (regName.equals("a")) continue;
                }
                
                String line = opName + " " + regName;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // constant argument:
            for(Integer constant:spec.allowed8bitConstants) {
                String line = opName + " " + constant;
                if (constant == 0) {
                    if (opName.equals("add") || opName.equals("sub")) continue;
                }                
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;                
            }
            
            // (hl):
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = opName + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = opName + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = opName + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        }
                    }
                }
            }
        }
        return true;
    }
    

    boolean precomputeIncDec(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code)
    {
        String opNames[] = {"inc", "dec"};
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l",
                             "bc", "de", "hl", 
                             "ix", "iy",
                             "ixh", "ixl", "iyh", "iyl"};
        for(String opName: opNames) {
            // register argument:
            if (!spec.allowedOps.contains(opName)) continue;
            for(String regName : regNames) {
                if (!spec.allowedRegisters.contains(regName)) continue;
                String line = opName + " " + regName;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // (hl):
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = opName + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = opName + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = opName + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        }
                    }
                }        
            }
        }
        return true;
    }
    
    
    boolean precomputeAddAdcSubSbc(List<SBOCandidate> candidates, Specification spec, 
                                   List<CPUOpDependency> allDependencies, CodeBase code)
    {
        if (!spec.allowedRegisters.contains("a")) return true;
        
        String opNames[] = {"add", "adc", "sub", "sbc"};
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l", "ixh", "ixl", "iyh", "iyl"};
        for(String opName : opNames) {
            if (!spec.allowedOps.contains(opName)) continue;
            // register argument:
            for(String regName : regNames) {
                if (!spec.allowedRegisters.contains(regName)) continue;
                String line = opName + " a," + regName;
                if (opName.equals("sub") && regName.startsWith("i")) continue;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // constant argument:
            for(Integer constant:spec.allowed8bitConstants) {
                String line = opName + " a," + constant;
                if (constant == 0) {
                    if (opName.equals("add") || opName.equals("sub")) continue;
                }
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // (hl):
            if (spec.allowRamUse) {
                String line = opName + " a,(hl)";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = opName + " a,("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = opName + " a,("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        }
                    }
                }
            }
        }
        
        String ops16bit[] = {"add hl,bc", "add hl,de", "add hl,hl", "add hl,sp",
                             "add ix,bc", "add ix,de", "add ix,ix", "add ix,sp",
                             "add iy,bc", "add iy,de", "add iy,iy", "add iy,sp",
        
                             "adc hl,bc", "adc hl,de", "adc hl,hl", "adc hl,sp",
                             "sbc hl,bc", "sbc hl,de", "sbc hl,hl", "sbc hl,sp"};
        for(String line:ops16bit) {
            StringTokenizer st = new StringTokenizer(line, " ,");
            if (!spec.allowedOps.contains(st.nextToken())) continue;
            if (!spec.allowedRegisters.contains(st.nextToken())) continue;
            if (!spec.allowedRegisters.contains(st.nextToken())) continue;
            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
        }
        return true;
    }    
    
    
    boolean precomputeLd(List<SBOCandidate> candidates, Specification spec, 
                         List<CPUOpDependency> allDependencies, CodeBase code)
    {
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String arg1 : regNames) {
            if (!spec.allowedRegisters.contains(arg1)) continue;
            for(String arg2 : regNames) {
                if (arg1.equals(arg2)) continue;
                if (!spec.allowedRegisters.contains(arg2)) continue;
                String line = "ld " + arg1 + "," + arg2;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = "ld (hl)," + arg1;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
                line = "ld " + arg1 + ",(hl)";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // constant argument:
            for(Integer constant:spec.allowed8bitConstants) {
                String line = "ld " + arg1 + "," + constant;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;                
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = "ld " + arg1 + ",("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                            line = "ld ("+reg+"+"+constant+")," + arg1;
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = "ld " + arg1 + ",("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;
                            line = "ld ("+reg+constant+")," + arg1;
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;
                        }
                    }
                }
            }
        }
        
        String regNames2[] = {"a", "b", "c", "d", "e"};
        String regNames3[] = {"ixh", "ixl", "iyh", "iyl"};
        for(String arg1 : regNames2) {
            if (!spec.allowedRegisters.contains(arg1)) continue;
            for(String arg2 : regNames3) {
                if (!spec.allowedRegisters.contains(arg2)) continue;
                String line = "ld " + arg1 + "," + arg2;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
        }
        for(String arg1 : regNames3) {
            if (!spec.allowedRegisters.contains(arg1)) continue;
            for(String arg2 : regNames2) {
                if (!spec.allowedRegisters.contains(arg2)) continue;
                String line = "ld " + arg1 + "," + arg2;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
        }
        
        // ld (hl)/ixh/ixl/iyh/iyl,n:
        {
            String args[] = {"ixh", "ixl", "iyh", "iyl"};
            for(String  arg1:args) {
                if (!spec.allowedRegisters.contains(arg1)) continue;
                for(Integer constant:spec.allowed8bitConstants) {
                    String line = "ld " + arg1 + "," + constant;
                    if (!precomputeOp(line, candidates, allDependencies, code)) return false;                
                }
            }
        }
        
        if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
            for(Integer constant:spec.allowed8bitConstants) {
                String line = "ld (hl)," + constant;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;                
            }
        }
        
//        if (spec.allowRamUse) {
            // ld (nn),a/bc/de/hl/ix/iy/sp
            // ...
        
            // ld a/bc/de/hl/ix/iy/sp,(nn)
            // ...            
//        }

        // ld (ix+o)/(iy+o),n
        if (spec.allowRamUse) {
            for(Integer constant:spec.allowed8bitConstants) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer offset:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = "ld ("+reg+"+"+offset+")," + constant;
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = "ld ("+reg+offset+")," + constant;
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        }
                    }
                }
            }
        }

        // ld bc/de/hl/sp/ix/iy,nn
        {
            String args[] = {"bc", "de", "hl", "sp", "ix", "iy"};
            for(String arg1:args) {
                if (!spec.allowedRegisters.contains(arg1)) continue;
                for(Integer constant:spec.allowed16bitConstants) {
                    String line = "ld " + arg1 + "," + constant;
                    if (!precomputeOp(line, candidates, allDependencies, code)) return false;                
                }
            }
        }
        
        String otherOps[] = {"ld a,i", "ld a,r", "ld i,a", "ld r,a",
                             "ld sp,hl", "ld sp,ix", "ld sp,iy",
                             "ld ixl,ixh", "ld ixh,ixl", 
                             "ld iyl,iyh", "ld iyh,iyl",
                             };
        for(String line:otherOps) {
            StringTokenizer st = new StringTokenizer(line, " ,");
            st.nextToken();
            if (!spec.allowedRegisters.contains(st.nextToken())) continue;
            if (!spec.allowedRegisters.contains(st.nextToken())) continue;
            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
        }
        if (spec.allowRamUse) {
            String otherRamOps[] = {"ld (bc),a", "ld (de),a",
                                    "ld a,(bc)", "ld a,(de)"};
            for(String line:otherRamOps) {
                StringTokenizer st = new StringTokenizer(line, " ,()");
                st.nextToken();
                if (!spec.allowedRegisters.contains(st.nextToken())) continue;
                if (!spec.allowedRegisters.contains(st.nextToken())) continue;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
            }            
        }
        return true;
    }
    
    
    boolean precomputeRotations(List<SBOCandidate> candidates, Specification spec, 
                                List<CPUOpDependency> allDependencies, CodeBase code)
    {
        String opNames[] = {"rlc", "rl", "rrc", "rr"};
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            for(String reg : regNames) {
                if (!spec.allowedRegisters.contains(reg)) continue;
                String line = op + " " + reg;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = op + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // (ix+o)/(iy+o):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = op + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = op + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        }
                    }
                }        
            }
        }        
        if (spec.allowedRegisters.contains("a")) {
            String otherOps[] = {"rlca", "rla", "rrca", "rra"};
            for(String line:otherOps) {
                if (!spec.allowedOps.contains(line)) continue;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
            }
        }
        return true;
    }


    boolean precomputeShifts(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code)
    {
        String opNames[] = {"sla", "sra", "srl", "sli"};
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            for(String reg : regNames) {
                if (!spec.allowedRegisters.contains(reg)) continue;
                
                // sla a is the same as add a,a, but slower
                if (op.equals("sla") && reg.equals("a") && spec.allowedOps.contains("add")) continue;
                String line = op + " " + reg;
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = op + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            
            // (ix+o)/(iy+o):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = op + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        } else {
                            String line = op + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                        }
                    }
                }   
            }
        }        
        return true;
    }
    
    
    boolean precomputeNegations(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code)
    {
        if (!spec.allowedRegisters.contains("a")) return true;

        String opNames[] = {"cpl", "neg"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            if (!precomputeOp(op, candidates, allDependencies, code)) return false;
        }        
        return true;
    }    
    
    
    boolean precomputeBits(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code)
    {
        String opNames[] = {"bit", "set", "res"};
        String registers[] = {"a", "b", "c", "d", "e", "h", "l"};
        String bits[] = {"0", "1", "2", "3", "4", "5", "6", "7"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            for(String bit: bits) {
                for(String reg: registers) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    String line = op + " " + bit + ","+reg;
                    if (!precomputeOp(line, candidates, allDependencies, code)) return false;
                }
                // (hl)
                if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                    String line = op + " " + bit + ",(hl)";
                    if (!precomputeOp(line, candidates, allDependencies, code)) return false;
                }
                // (ix+o)/(iy+o):
                if (spec.allowRamUse) {
                    for(String reg:new String[]{"ix","iy"}) {
                        if (!spec.allowedRegisters.contains(reg)) continue;
                        for(Integer constant:spec.allowedOffsetConstants) {
                            if (constant >= 0) {
                                String line = op + " " + bit +",("+reg+"+"+constant+")";
                                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                            } else {
                                String line = op + " " + bit +", ("+reg+constant+")";
                                if (!precomputeOp(line, candidates, allDependencies, code)) return false;            
                            }
                        }
                    }
                }
            }
        }
        return true;
    }   
    
    
    boolean precomputeCarry(List<SBOCandidate> candidates, Specification spec, 
                            List<CPUOpDependency> allDependencies, CodeBase code)
    {
        String opNames[] = {"ccf", "scf"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            if (!precomputeOp(op, candidates, allDependencies, code)) return false;
        }
        return true;
    }   
    
    
    boolean precomputeJumps(List<SBOCandidate> candidates, Specification spec, 
                            List<CPUOpDependency> allDependencies, CodeBase code)
    {
        if (spec.allowedOps.contains("jp")) {
            for(String flag:new String[]{"", "z,", "po,", "pe,", "p,", "nz,", "nc,", "m,", "c,"}) {
                String line = "jp " + flag + "$";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
            if (!precomputeOp("jp hl", candidates, allDependencies, code)) return false;
            if (!precomputeOp("jp ix", candidates, allDependencies, code)) return false;
            if (!precomputeOp("jp iy", candidates, allDependencies, code)) return false;
        }
        if (spec.allowedOps.contains("jr")) {
            for(String flag:new String[]{"", "z,", "nz,", "nc,", "c,"}) {
                String line = "jr " + flag + "$";
                if (!precomputeOp(line, candidates, allDependencies, code)) return false;
            }
        }
        if (spec.allowedOps.contains("djnz")) {
            if (!precomputeOp("djnz $", candidates, allDependencies, code)) return false;
        }
        return true;
    }

}

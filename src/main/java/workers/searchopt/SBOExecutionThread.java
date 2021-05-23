/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpDependency;
import code.CPUOpSpec;
import code.CodeBase;
import code.Expression;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.PlainZ80Memory;
import util.microprocessor.ProcessorException;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class SBOExecutionThread extends Thread {
    String threadID = "";
    
    int searchType;
    
    MDLConfig config;
    Specification spec;
    int nDependencies;
    List<CPUOpDependency> allDependencies;
            
    RegisterNames eightBitRegistersToRandomize[];
    boolean showNewBestDuringSearch = true;
    CodeBase code;
    
    Random rand = new Random();
    Z80Core z80 = null;
    PlainZ80Memory z80Memory = null;
    
    // Store the current program, and additional info to create jumps afterwards:
    CPUOp currentOps[] = null;
    int currentOpsAddresses[] = null;
    int currentAbsoluteJumps_n = 0;
    int currentAbsoluteJumps[] = null;  // stores the indexes of "jp"s
    int currentRelativeJumps_n = 0;
    int currentRelativeJumps[] = null;  // stores the indexes of "jr"s or "djnz"s
    
    int minimumInstructionTime = 1;
    
    // The "dependencies" array, contains the set of dependencies (Registers/flags) that
    // have already been set by previous instructions:
    // - 1 means they are set from the start state
    // - 2 means they are set by an instruction
    // - 3 means both
    int currentDependencies[][] = null;
    boolean goalDependencies[] = null;
    
    SBOGlobalSearchState globalState;
    // local state:
    int bestIdx = 0;
    List<CPUOp> bestOps = null;
    int bestSize = 0;
    float bestTime = 0;
    long solutionsEvaluated = 0;
    
    int codeMaxOps;
    int codeMaxAddress;
    int maxSimulationTime;
    
    public SBOExecutionThread(String a_threadID,
                              Specification a_spec, List<CPUOpDependency> a_allDependencies, 
                              boolean a_goalDependencies[],
                              SBOGlobalSearchState a_best, RegisterNames a_eightBitRegistersToRandomize[], 
                              boolean a_showNewBestDuringSearch, CodeBase a_code,
                              MDLConfig a_config,
                              int a_searchType, int a_codeMaxOps, int a_codeMaxAddress, int a_maxSimulationTime)
    {
        threadID = a_threadID;
        config = a_config;
        spec = a_spec;
        allDependencies = a_allDependencies;
        nDependencies = allDependencies.size();
        goalDependencies = a_goalDependencies;
        globalState = a_best;
        eightBitRegistersToRandomize = a_eightBitRegistersToRandomize;
        showNewBestDuringSearch = a_showNewBestDuringSearch;
        code = a_code;
        
        z80Memory = new PlainZ80Memory();
        z80 = new Z80Core(z80Memory, new PlainZ80IO(), new CPUConfig(config));
        
        // Run the search process to generate code:
        // Search via iterative deepening:
        currentOps = new CPUOp[spec.maxOps];
        currentOpsAddresses = new int[spec.maxOps+1];
        currentAbsoluteJumps_n = 0;
        currentAbsoluteJumps = new int[spec.maxOps];
        currentRelativeJumps_n = 0;
        currentRelativeJumps = new int[spec.maxOps];
        
        currentDependencies = new int[spec.maxOps+1][nDependencies];
        {
            boolean initialDependencies[] = spec.getInitialDependencies(allDependencies);
            for(int i = 0;i<nDependencies;i++) {
                currentDependencies[0][i] = initialDependencies[i] ? 1:0;
            }
        }
        config.debug("Initial dependency set: " + Arrays.toString(currentDependencies[0]));        

        searchType = a_searchType;
        codeMaxOps = a_codeMaxOps;
        codeMaxAddress = a_codeMaxAddress;
        maxSimulationTime = a_maxSimulationTime;
        
        minimumInstructionTime = -1;
        for(CPUOpSpec spec:config.opParser.getOpSpecs()) {
            int minTime = spec.times[0];
            if (spec.times.length > 1) {
                minTime = Math.min(minTime, spec.times[1]);
            }
            if (minimumInstructionTime == -1 || minTime < minimumInstructionTime) {
                minimumInstructionTime = minTime;
            }
        }
    }
    
    
    @Override
    public void run() {
        try {
            while(true) {
                SBOCandidate op = globalState.getNextStartingOp();
                if (op == null) return;

                List<SBOCandidate> candidateOps = new ArrayList<>();
//                System.out.println("thread " + threadID + " <start> " + op.op);
                candidateOps.add(op);                
                // Loop, just in case something changes in between because of another thread:
                do {
                    bestIdx = globalState.bestIdx;
                    bestOps = globalState.bestOps;
                    bestSize = globalState.bestSize;
                    bestTime = globalState.bestTime;
                }while(bestIdx != globalState.bestIdx);
                solutionsEvaluated = 0;

                if (spec.searchType == SearchBasedOptimizer.SEARCH_ID_OPS) {
                    depthFirstSearch(0, spec.codeStartAddress, candidateOps);
                } else if (spec.searchType == SearchBasedOptimizer.SEARCH_ID_BYTES) {
                    depthFirstSearch(0, spec.codeStartAddress, candidateOps);
                } else if (spec.searchType == SearchBasedOptimizer.SEARCH_ID_CYCLES) {
                    depthFirstSearch_timeBounded(0, 0, spec.codeStartAddress, candidateOps);
                }
                globalState.incrementSolutionsEvaluated(solutionsEvaluated);
//                System.out.println("    thread " + threadID + " end with " + solutionsEvaluated);
            }
        } catch (Exception e) {
            config.error(e.getMessage());
            config.error(Arrays.toString(e.getStackTrace()));
        }
    }
    
        
    boolean depthFirstSearch(int depth, int codeAddress,
                             List<SBOCandidate> candidateOps) throws Exception
    {
        if (depth >= codeMaxOps || codeAddress >= codeMaxAddress) {
            return evaluateSolution(depth, 0, 0, codeAddress);
        } else {
            boolean found = false;
            // the very last op must contribute to the goal:
            for(SBOCandidate candidate : candidateOps) {
                int nextAddress = codeAddress + candidate.bytes.length;
                if (nextAddress > codeMaxAddress) continue;
                if (!candidate.directContributionToGoal &&
                    (depth == codeMaxOps-1 || codeMaxAddress == nextAddress)) {
                    continue;
                }
                int size = nextAddress - spec.codeStartAddress;
                if (!canBeBest(depth+1, size)) continue;
                boolean dependenciesSatisfied = true;
                for(int i = 0;i<nDependencies;i++) {
                    if (candidate.inputDependencies[i] && currentDependencies[depth][i] == 0) {
                        dependenciesSatisfied = false;
                        break;
                    }
                    currentDependencies[depth+1][i] = currentDependencies[depth][i] | candidate.outputDependencies[i];
                }
                if (!dependenciesSatisfied) continue;
                if (depth == codeMaxOps-1 || codeMaxAddress == nextAddress) {
                    // this is the last op we can add, so all output dependencies MUST be satisfied:
                    boolean goalDependenciesSatisfied = true;
                    for(int i: spec.goalDependencyIndexes) {
                          if (!spec.goalDependenciesSatisfiedFromTheStart[i] &&
                              currentDependencies[depth+1][i] < 2) {
                            goalDependenciesSatisfied = false;
                            break;
                        }
                    }
                    if (!goalDependenciesSatisfied) continue;
                }
                System.arraycopy(candidate.bytes, 0, z80Memory.memory, codeAddress, candidate.bytes.length);
                currentOps[depth] = candidate.op;
                currentOpsAddresses[depth] = codeAddress;
                if (candidate.isAbsoluteJump) {
                    // It does not make sense to have an unconditional jump before a conditional one:
                    if (candidate.isUnconditionalJump && currentAbsoluteJumps_n == 0 && currentRelativeJumps_n == 0) continue;
                    currentAbsoluteJumps[currentAbsoluteJumps_n] = depth;
                    currentAbsoluteJumps_n++;
                    if (depthFirstSearch(depth+1, nextAddress, candidate.potentialFollowUps)) {
                        found = true;
                        // we keep going, in case we find a solution of the same size, but faster
                    }
                    currentAbsoluteJumps_n--;
                } else if (candidate.isRelativeJump) {
                    // It does not make sense to have an unconditional jump before a conditional one:
                    if (candidate.isUnconditionalJump && currentAbsoluteJumps_n == 0 && currentRelativeJumps_n == 0) continue;
                    currentRelativeJumps[currentRelativeJumps_n] = depth;
                    currentRelativeJumps_n++;
                    if (depthFirstSearch(depth+1, nextAddress, candidate.potentialFollowUps)) {
                        found = true;
                        // we keep going, in case we find a solution of the same size, but faster
                    }
                    currentRelativeJumps_n--;
                } else {
                    if (depthFirstSearch(depth+1, nextAddress, candidate.potentialFollowUps)) {
                        found = true;
                        // we keep going, in case we find a solution of the same size, but faster
                    }
                }
            }
            return found;
        }
    }

    
    boolean depthFirstSearch_timeBounded(int depth, int currentTime, int codeAddress,
                                         List<SBOCandidate> candidateOps) throws Exception
    {
        if (depth >= codeMaxOps || codeAddress >= codeMaxAddress || currentTime >= maxSimulationTime) {
            return evaluateSolution(depth, 0, 0, codeAddress);
        } else if (currentTime > maxSimulationTime - minimumInstructionTime) {
            // We cannot fit any instruction in this small amount of time, so just fail
            return false;
        } else {
            boolean found = false;
            // the very last op must contribute to the goal:
            for(SBOCandidate candidate : candidateOps) {
                int nextAddress = codeAddress + candidate.bytes.length;
                if (nextAddress > codeMaxAddress) continue;
                if (!candidate.directContributionToGoal &&
                    (depth == codeMaxOps-1 || codeMaxAddress == nextAddress)) {
                    continue;
                }
                int nextTime = currentTime + candidate.op.spec.times[candidate.op.spec.times.length-1];
                if (nextTime > maxSimulationTime) continue;
                int size = codeAddress - spec.codeStartAddress;
                if (!canBeBest(depth+1, size)) continue;
                boolean dependenciesSatisfied = true;
                for(int i = 0;i<nDependencies;i++) {
                    if (candidate.inputDependencies[i] && currentDependencies[depth][i] == 0) {
                        dependenciesSatisfied = false;
                        break;
                    }
                    currentDependencies[depth+1][i] = currentDependencies[depth][i] | candidate.outputDependencies[i];
                }
                if (!dependenciesSatisfied) continue;
                if (depth == codeMaxOps-1 || codeMaxAddress == nextAddress || nextTime == maxSimulationTime) {
                    // This is the last op we can add, so all output dependencies MUST be satisfied:
                    boolean goalDependenciesSatisfied = true;
                    for(int i: spec.goalDependencyIndexes) {
                          if (!spec.goalDependenciesSatisfiedFromTheStart[i] &&
                              currentDependencies[depth+1][i] < 2) {
                            goalDependenciesSatisfied = false;
                            break;
                        }
                    }
                    if (!goalDependenciesSatisfied) continue;
                }                                    
                               
                System.arraycopy(candidate.bytes, 0, z80Memory.memory, codeAddress, candidate.bytes.length);
                currentOps[depth] = candidate.op;
                currentOpsAddresses[depth] = codeAddress;
                if (candidate.isAbsoluteJump) {
                    // It does not make sense to have an unconditional jump before a conditional one:
                    if (candidate.isUnconditionalJump && currentAbsoluteJumps_n == 0 && currentRelativeJumps_n == 0) continue;
                    currentAbsoluteJumps[currentAbsoluteJumps_n] = depth;
                    currentAbsoluteJumps_n++;
                    if (depthFirstSearch_timeBounded(depth+1, 
                                                     nextTime, 
                                                     nextAddress,
                                                     candidate.potentialFollowUps)) {
                        found = true;
                        // we keep going, in case we find a solution of the same speed, but smaller
                    }
                    currentAbsoluteJumps_n--;
                } else if (candidate.isRelativeJump) {
                    // It does not make sense to have an unconditional jump before a conditional one:
                    if (candidate.isUnconditionalJump && currentAbsoluteJumps_n == 0 && currentRelativeJumps_n == 0) continue;
                    currentRelativeJumps[currentRelativeJumps_n] = depth;
                    currentRelativeJumps_n++;
                    if (depthFirstSearch_timeBounded(depth+1, 
                                                     nextTime, 
                                                     nextAddress,
                                                     candidate.potentialFollowUps)) {
                        found = true;
                        // we keep going, in case we find a solution of the same speed, but smaller
                    }
                    currentRelativeJumps_n--;
                } else {
                    if (depthFirstSearch_timeBounded(depth+1, 
                                                     nextTime, 
                                                     nextAddress,
                                                     candidate.potentialFollowUps)) {
                        found = true;
                        // we keep going, in case we find a solution of the same speed, but smaller
                    }
                }
            }
            return found;
        }
    }    
    

    final boolean evaluateSolution(int depth, int nextAbsoluteJump, int nextRelativeJump, int breakPoint)
    {
        if (currentAbsoluteJumps_n > nextAbsoluteJump) {
            currentOpsAddresses[depth] = breakPoint;
            boolean solutionFound = false;
            int jumpIndex = currentAbsoluteJumps[nextRelativeJump];
            int start = 0;
            if (!spec.allowLoops) {
                start = jumpIndex+1;
            }
            for(int j = start;j<=depth;j++) {
                if (j == jumpIndex || j == jumpIndex + 1) continue;
                // set the address (bytes and op):
                currentOps[jumpIndex] = new CPUOp(currentOps[jumpIndex]);
                currentOps[jumpIndex].args.set(currentOps[jumpIndex].args.size()-1,
                        Expression.constantExpression(currentOpsAddresses[j], config));
                z80Memory.writeWord(currentOpsAddresses[jumpIndex]+1, 
                                    currentOpsAddresses[j]);
                if (evaluateSolution(depth, nextAbsoluteJump+1, nextRelativeJump, breakPoint)) {
                    solutionFound = true;
                }
            }
            return solutionFound;
        } else if (currentRelativeJumps_n > nextRelativeJump) {
            currentOpsAddresses[depth] = breakPoint;
            boolean solutionFound = false;
            int jumpIndex = currentRelativeJumps[nextRelativeJump];
            int start = 0;
            if (!spec.allowLoops) {
                start = jumpIndex+1;
            }
            for(int j = start;j<=depth;j++) {
                if (j == jumpIndex || j == jumpIndex + 1) continue;
                // set the address (bytes and op):
                currentOps[jumpIndex] = new CPUOp(currentOps[jumpIndex]);
                currentOps[jumpIndex].args.set(currentOps[jumpIndex].args.size()-1,
                        Expression.constantExpression(currentOpsAddresses[j], config));
                z80Memory.writeByte(currentOpsAddresses[jumpIndex]+1, 
                                    currentOpsAddresses[j] - currentOpsAddresses[jumpIndex+1]);
                if (evaluateSolution(depth, nextAbsoluteJump, nextRelativeJump+1, breakPoint)) {
                    solutionFound = true;
                }
            }
            return solutionFound;
        }
        
        try {
            while(bestIdx != globalState.bestIdx) {
                bestIdx = globalState.bestIdx;
                bestOps = globalState.bestOps;
                bestSize = globalState.bestSize;
                bestTime = globalState.bestTime;                
            }
            
            // Print statement to print sequences and visually inspect if there are any prunable ones:
//            if (depth == 3) System.out.println(Arrays.toString(currentOps));
            
            solutionsEvaluated++;

            int size = breakPoint - spec.codeStartAddress;
            float time = -1;
            for(int i = 0; i < spec.numberOfRandomSolutionChecks; i++) {
                int time2 = evaluateSolutionInternal(breakPoint, spec.precomputedTestCases[i]);
                if (time2 < 0) {
                    return false;
                }
                switch(spec.searchTimeCalculation) {
                    case SearchBasedOptimizer.SEARCH_TIME_WORST:
                        time = Math.max(time, time2);
                        break;
                    case SearchBasedOptimizer.SEARCH_TIME_BEST:
                        if (time == -1 || time2 < time) time = time2;
                        break;
//                    case SearchBasedOptimizer.SEARCH_TIME_AVERAGE:
                    default:
                        time += time2;
                        break;
                }
            }
            if (spec.searchTimeCalculation == SearchBasedOptimizer.SEARCH_TIME_AVERAGE) {
                time /= spec.numberOfRandomSolutionChecks;
            }
            if (isBetter(depth, size, time)) {
                List<CPUOp> bestOps = new ArrayList<>();
                for(int i = 0;i<depth;i++) {
                    CPUOp op = currentOps[i];
                    if (op.isJump()) {
                        // relativize the jump to the current address (we know it must be an integer constant):
                        int offset = op.args.get(op.args.size()-1).integerConstant -  currentOpsAddresses[i];
                        op = new CPUOp(op);
                        if (offset >= 0) {
                            op.args.set(op.args.size()-1, 
                                    Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                                            Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, null, code, config), 
                                            Expression.constantExpression(offset, config), config));
                        } else {
                            op.args.set(op.args.size()-1, 
                                    Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                                            Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, null, code, config), 
                                            Expression.constantExpression(-offset, config), config));
                        }
                    }
                    bestOps.add(op);
                }
                globalState.newBest(bestOps, size, time);

                if (showNewBestDuringSearch) {
                    config.info("New solution found (size: "+size+" bytes, time: " + time + " " + config.timeUnit + "s):");
                    for(CPUOp op:bestOps) {
                        config.info("    " + op);
                    }
                }
            }
            return true;
        } catch(ProcessorException e) {
            // This could happen if the program self-modifies itself and garbles the codebase,
            // resulting in an inexisting opcode.
            return false;
        }            
    }
    
    
    // return -1 is solution fails
    // return time it takes if solution succeeds
    // "i" is the index of the 
    final int evaluateSolutionInternal(int breakPoint, Specification.PrecomputedTestCase testCase) throws ProcessorException
    {
        // evaluate solution:
        z80.shallowReset();

        for(CPUConstants.RegisterNames register: eightBitRegistersToRandomize) {
            z80.setRegisterValue(register, rand.nextInt(256));
        }
        z80.setProgramCounter(spec.codeStartAddress);
                                 
        // randomize the memory contents:
        // ...
        
        // execute initial state:
        testCase.initCPU(z80);
        
        while(z80.getProgramCounter() < breakPoint && 
              z80.getTStates() < spec.maxSimulationTime) {
            z80.executeOneInstruction();
        }
        if (z80.getTStates() >= spec.maxSimulationTime) return -1;
        
        // check if the solution worked:
        if (testCase.checkGoalState(z80)) {
            return (int)z80.getTStates();
        } else {
            return -1;
        }
    }    
    
    
    public boolean canBeBest(int nOps, int size)
    {
        return !(bestOps != null &&
                (size > bestSize ||
                (size == bestSize && nOps > bestOps.size())));
    }
    
    
    public boolean isBetter(int nOps, int size, float time) {
        return bestOps == null ||
                size < bestSize ||
                (size == bestSize && nOps < bestOps.size()) ||
                (size == bestSize && nOps == bestOps.size() && time < bestTime);
    }
    
    
    public void newBest(List<CPUOp> ops, int size, float time)
    {
        if (isBetter(ops.size(), size, time)) {
            bestIdx ++;
            bestOps = ops;
            bestSize = size;
            bestTime = time;
        }
    }    
}

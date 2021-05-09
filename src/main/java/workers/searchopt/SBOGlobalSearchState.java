/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.CPUOp;
import java.util.List;

/**
 *
 * @author santi
 */
public class SBOGlobalSearchState {
    long solutionsEvaluated = 0;
    
    int bestIdx = 0;    // Each time the best solution changes, this gets incremented by 1.
                        // It is used to trigger a slower synchronization only when needed.
    List<CPUOp> bestOps = null;
    int bestSize = 0;
    float bestTime = 0;
    
    int nextCandidateOpAtDepth0 = 0;
    List<SBOCandidate> candidateOpsAtDepth0;

    
    public SBOGlobalSearchState()
    {
    }
        
    
    public void init(List<SBOCandidate> allCandidateOps, boolean isZeroDepth)
    {
        nextCandidateOpAtDepth0 = 0;
        candidateOpsAtDepth0 = allCandidateOps;
        // at depth 0, we just need to do one check (as none of these ops will be added anyway):
        if (isZeroDepth) {
            nextCandidateOpAtDepth0 = candidateOpsAtDepth0.size()-1;
        }
    }
    
    
    synchronized public SBOCandidate getNextStartingOp()
    {
        if (nextCandidateOpAtDepth0 >= candidateOpsAtDepth0.size()) {
            return null;
        } else {
            nextCandidateOpAtDepth0++;
            return candidateOpsAtDepth0.get(nextCandidateOpAtDepth0 - 1);
        }
    }
    
    
    public void incrementSolutionsEvaluated(long amount)
    {
        solutionsEvaluated+=amount;
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
    
    
    synchronized public void newBest(List<CPUOp> ops, int size, float time)
    {
        if (isBetter(ops.size(), size, time)) {
            bestIdx ++;
            bestOps = ops;
            bestSize = size;
            bestTime = time;
        }
    }
    
    
//    synchronized public void sync(SBOGlobalSearchState s)
//    {
//        if (s.bestOps != null &&
//            isBetter(s.bestOps.size(), s.bestSize, s.bestTime)) {
//            bestIdx ++;
//            bestOps = s.bestOps;
//            bestSize = s.bestSize;
//            bestTime = s.bestTime;            
//        }
//        solutionsEvaluated += s.solutionsEvaluated;
//    }
//
//
//    synchronized public void copyToAnotherState(SBOGlobalSearchState s)
//    {
//        s.bestOps = bestOps;
//        s.bestSize = bestSize;
//        s.bestTime = bestTime;
//    }

}

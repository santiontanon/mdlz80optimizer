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
import java.util.List;

/**
 *
 * @author santi
 */
public class SBOCandidate {
    public int bytes[] = null;
    public CPUOp op;

    // Cached dependencies (which registers/flags do these ops depend on, and which do they set):
    public boolean inputDependencies[] = null;
    public boolean outputDependencies[] = null;
    public boolean directContributionToGoal = false;
    
    // Not all ops make sense after a certain op, (e.g. "ld a,b; ld b,a"). Hence,
    // the set of ops that make sense after another op are precalculated, and stored here:
    public List<SBOCandidate> potentialFollowUps = null;
    
    public SBOCandidate(CPUOp a_op, List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        op = a_op;
        
        // precompile it to bytes:
        List<Integer> tmp = op.assembleToBytes(null, code, config);
        if (tmp == null) {
            config.error("Cannot assemble CPUOp to bytes: " + op);
        } else {
            bytes = new int[tmp.size()];
            for(int i = 0;i<tmp.size();i++) {
                bytes[i] = tmp.get(i);
            }
        }
        
        // precompute the dependencies:
        inputDependencies = new boolean[allDependencies.size()];
        outputDependencies = new boolean[allDependencies.size()];
        for(int i = 0;i<allDependencies.size();i++) {
            inputDependencies[i] = false;
            outputDependencies[i] = false;
        }
        for(CPUOpDependency dep:op.getInputDependencies()) {
            for(int i = 0;i<allDependencies.size();i++) {
                if (dep.match(allDependencies.get(i))) {
                    inputDependencies[i] = true;
                }
            }
        }
        for(CPUOpDependency dep:op.getOutputDependencies()) {
            for(int i = 0;i<allDependencies.size();i++) {
                if (dep.match(allDependencies.get(i))) {
                    outputDependencies[i] = true;
                }
            }
        }
    }
}

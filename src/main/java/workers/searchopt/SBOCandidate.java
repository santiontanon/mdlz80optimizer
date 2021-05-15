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
import java.util.ArrayList;
import java.util.HashMap;
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
    public int outputDependencies[] = null;
    public boolean directContributionToGoal = false;
    public boolean isAbsoluteJump = false;
    public boolean isRelativeJump = false;
    public boolean isUnconditionalJump = false;
    
    // Not all ops make sense after a certain op, (e.g. "ld a,b; ld b,a"). Hence,
    // the set of ops that make sense after another op are precalculated, and stored here:
    public List<SBOCandidate> potentialFollowUps = null;
    public List<SBOCandidate> prefix = null;    // the ops that "potentialFollowUps" assume (this is not used during search,
                                                // only used to verify the precalculations are sound)
    
    
    public SBOCandidate(CodeStatement a_s, List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        op = a_s.op;
        
        if (op.isJump()) {
            if (op.isRelativeJump()) {
                isRelativeJump = true;
            } else {
                isAbsoluteJump = true;
            }
            if (!op.isConditional()) {
                isUnconditionalJump = true;
            }
        }
        
        // precompile it to bytes:
        List<Integer> tmp = op.assembleToBytes(a_s, code, config);
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
        outputDependencies = new int[allDependencies.size()];
        for(int i = 0;i<allDependencies.size();i++) {
            inputDependencies[i] = false;
            outputDependencies[i] = 0;
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
                    outputDependencies[i] = 2;
                }
            }
        }
    }
    
    
    public SBOCandidate(SBOCandidate c, SBOCandidate previous, 
                        int maxLength, HashMap<String, SBOCandidate> candidateOpsByPrefix,
                        MDLConfig config)
    {
        bytes = c.bytes;
        op = c.op;
        inputDependencies = c.inputDependencies;
        outputDependencies = c.outputDependencies;
        directContributionToGoal = c.directContributionToGoal;
        isAbsoluteJump = c.isAbsoluteJump;
        isRelativeJump = c.isRelativeJump;
        prefix = new ArrayList<>();
        if (previous.prefix != null) {
            prefix.addAll(previous.prefix);
        }
        prefix.add(previous);
        while(prefix.size() > maxLength - 2) {
            prefix.remove(0);
        }
    }
    
    
    public String prefixString()
    {
        String prefixStr = "";
        if (prefix != null) {
            for(SBOCandidate c:prefix) {
                prefixStr += c.op + ";";
            }
         }
        prefixStr += op + ";";
        return prefixStr;
    }
    
    
    public String prefixString(SBOCandidate previous, int maxLength)
    {
        List<SBOCandidate> l = new ArrayList<>();
        if (previous.prefix != null) l.addAll(previous.prefix);
        l.add(previous);
        l.add(this);
        while(l.size() > maxLength - 1) {
            l.remove(0);
        }
        String prefixStr = "";
        for(SBOCandidate c:l) {
            prefixStr += c.op + ";";
        }
        return prefixStr;
    }
    
    
    // "maxLength" is the length of the sequences to consider for precomputing
    public static boolean precomputeCandidates(
            HashMap<String, SBOCandidate> candidateOpsByPrefix,
            List<SBOCandidate> allCandidateOps,
            List<CPUOpDependency> allDependencies,
            Specification spec,
            CodeBase code,
            int maxLength,
            MDLConfig config)
    {
        List<SBOCandidate> open = new ArrayList<>();
        List<SBOCandidate> sequence = new ArrayList<>();
        
        int carryFlagDepIdx = -1;
        for(int i = 0;i<allDependencies.size();i++) {
            if (allDependencies.get(i).flag != null &&
                allDependencies.get(i).flag.equals("C")) {
                carryFlagDepIdx = i;
                break;
            }
        }
        
        // initial set:
        for(SBOCandidate op:allCandidateOps) {
            String prefixStr = op.prefixString();
            candidateOpsByPrefix.put(prefixStr, op);
            open.add(op);
        }
        
        // Precalculate potential followups to each op:
        int n = 0;
        while(!open.isEmpty()) {
            n++;
            SBOCandidate op = open.remove(0);
            op.potentialFollowUps = new ArrayList<>();
            for(SBOCandidate op2:allCandidateOps) {
                sequence.clear();
                if (op.prefix != null) sequence.addAll(op.prefix);
                sequence.add(op);
                sequence.add(op2);
                if (sequence.size() > maxLength) {
                    config.error("precomputeCandidates: sequence is longer than maxLength!!");
                    return false;
                }
                if (sequenceMakesSense(sequence, spec, carryFlagDepIdx, code)) {
                    String prefixStr = op2.prefixString(op, maxLength);
                    if (candidateOpsByPrefix.containsKey(prefixStr)) {
                        op2 = candidateOpsByPrefix.get(prefixStr);
                    } else {
                        op2 = new SBOCandidate(op2, op, maxLength, candidateOpsByPrefix, config);
                        open.add(op2);
                        candidateOpsByPrefix.put(prefixStr, op2);
                    }
                    op.potentialFollowUps.add(op2);
                }
            }
        }
        
        int count = 0;
        for(String key:candidateOpsByPrefix.keySet()) {
            count += candidateOpsByPrefix.get(key).potentialFollowUps.size();
        }
        System.out.println("precomputeCandidates (allCandidateOps = "+allCandidateOps.size()+", maxLength = " + maxLength + ") = " + count);
        
        return true;
    }
    
    
    static String sequenceString(List<SBOCandidate> sequence)
    {
        String str = "";
        for(SBOCandidate c:sequence) {
            str += c.op + "; ";
        }
        return str.strip();
    }


    static boolean sequenceMakesSense(List<SBOCandidate> sequence, Specification spec, 
                                      int carryFlagDepIdx, CodeBase code)
    {
        // pair-wise dependencies:
        for(int i = 0;i<sequence.size()-1;i++) {
            if (!sequenceMakesSensePair(sequence.get(i), sequence.get(i+1), spec, code)) return false;
        }
        
        // check for instructions that get masked out (all its effects are overwritten by subsequent instructions):
        for(int i = 0;i<sequence.size()-2;i++) {
            SBOCandidate op = sequence.get(i);
            if (op.op.isJump()) continue;
            boolean resultsUsed = false;
            int depsRemaining = 0;
            boolean outDeps[] = new boolean[op.outputDependencies.length];
            for(int k = 0;k<op.outputDependencies.length;k++) {
                outDeps[k] = (op.outputDependencies[k] != 0);
                if (outDeps[k]) depsRemaining += 1;
            }
            for(int j = i+1;j<sequence.size();j++) {
                SBOCandidate op2 = sequence.get(j);
                for(int k = 0;k<op.outputDependencies.length;k++) {
                    if (outDeps[k]) {
                        if (op2.inputDependencies[k]) {
                            resultsUsed = true;
                            break;
                        }
                        if (op2.outputDependencies[k] != 0) {
                            outDeps[k] = false;
                            depsRemaining --;
                            if (depsRemaining == 0) break;
                        }
                    }
                }
                if (resultsUsed || depsRemaining == 0) break;
            }
            if (!resultsUsed && depsRemaining == 0) {
                return false;
            }
        }
        
        
        // Canonical order for sequences of length > 2:
        for(int i = 0;i<sequence.size();i++) {
            SBOCandidate op1 = sequence.get(i);
            for(int j = i+1;j<sequence.size()-1;j++) {
                SBOCandidate op2 = sequence.get(j);
                
                // Get all the dependencies that will be masked by subsequence instructions:
                boolean maskedDeps[] = new boolean[op1.outputDependencies.length];
                boolean restInputDeps[] = new boolean[op1.outputDependencies.length];
                for(int d = 0;d<maskedDeps.length;d++) {
                    maskedDeps[d] = false;
                    restInputDeps[d] = false;
                }
                for(int k = j+1;k<sequence.size();k++) {
                    SBOCandidate op3 = sequence.get(k);
                    for(int d = 0;d<maskedDeps.length;d++) {
                        if (op3.outputDependencies[d] != 0) maskedDeps[d] = true;
                    }
                }
                for(int k = sequence.size()-1;k>=j+1;k--) {
                    SBOCandidate op3 = sequence.get(k);
                    for(int d = 0;d<maskedDeps.length;d++) {
                        if (op3.outputDependencies[d] != 0) restInputDeps[d] = false;
                    }
                    for(int d = 0;d<maskedDeps.length;d++) {
                        if (op3.inputDependencies[d]) restInputDeps[d] = true;
                    }
                }
                
//                boolean op2outputIsSuperset = true;
                boolean op2dependsOnOp1 = false;
                boolean op1WouldDependOnOp2 = false;
                boolean op1op2NonMaskedOutputsDisjoint = true;
                boolean restDependsOnLast = false;
                for(int d = 0;d<op1.outputDependencies.length;d++) {
//                    if (op1.outputDependencies[d] != 0 && op2.outputDependencies[d] == 0) {
//                        op2outputIsSuperset = false;
//                    }
                    if (op1.outputDependencies[d] != 0 && op2.inputDependencies[d]) {
                        op2dependsOnOp1 = true;
                    }
                    if (op2.outputDependencies[d] != 0 && op1.inputDependencies[d]) {
                        op1WouldDependOnOp2 = true;
                    }
                    if (!maskedDeps[d] && op1.outputDependencies[d] != 0 && op2.outputDependencies[d] != 0) {
                        op1op2NonMaskedOutputsDisjoint = false;
                    }
                }
                for(int d = 0;d<op1.outputDependencies.length;d++) {
                    if (restInputDeps[d]) {
                        if (op1.outputDependencies[d] != 0 &&
                            op2.outputDependencies[d] != 0) {
                            restDependsOnLast = true;
                        }
                    }
                }
                
                if (!op2dependsOnOp1 && 
                    !op1WouldDependOnOp2 && 
                    op1op2NonMaskedOutputsDisjoint &&
                    !restDependsOnLast &&
                    !op1.op.isJump() &&
                    !op2.op.isJump()) {
                    // general canonical order of instructions that are independent:
                    if (op1.op.toString().compareTo(op2.op.toString()) > 0) {
//                        System.out.println(sequenceString(sequence));
                        return false;
                    }
                }
                
                if ((op1.op.spec.getName().equals("add")) &&
                    op2.op.spec.getName().equals(op1.op.spec.getName()) &&
                    op1.op.args.get(0).registerOrFlagName.equals(op2.op.args.get(0).registerOrFlagName) &&
                    (!op1.op.args.get(1).isRegister(code) || !op1.op.args.get(0).registerOrFlagName.equals(op1.op.args.get(1).registerOrFlagName)) &&
                    (!op2.op.args.get(1).isRegister(code) || !op2.op.args.get(0).registerOrFlagName.equals(op2.op.args.get(1).registerOrFlagName)) &&
                    maskedDeps[carryFlagDepIdx] &&
                    !restInputDeps[carryFlagDepIdx]) {
                    // cannonical order of additions:
                    if (op1.op.toString().compareTo(op2.op.toString()) > 0) {
//                        System.out.println("- removed: " + sequenceString(sequence));
                        return false;
                    }
                }
                
            }
        }
        
//        System.out.println(sequenceString(sequence));
        return true;
    }
    
    
    static boolean sequenceMakesSensePair(SBOCandidate op1, SBOCandidate op2, Specification spec, CodeBase code)
    {
        // These sequences are already captured below, so, no need to check for them:
        // - ld X, Y; ld X, Z
        // op1, op2 is useless if op2's output is a superseteq of op1's, but op2 does not take any input dependency from op1
        boolean op2outputIsSuperset = true;
        boolean op2dependsOnOp1 = false;
        boolean op1WouldDependOnOp2 = false;
        boolean op1op2OutputsDisjoint = true;
        for(int i = 0;i<op1.outputDependencies.length;i++) {
            if (op1.outputDependencies[i] != 0 && op2.outputDependencies[i] == 0) {
                op2outputIsSuperset = false;
            }
            if (op1.outputDependencies[i] != 0 && op2.inputDependencies[i]) {
                op2dependsOnOp1 = true;
            }
            if (op2.outputDependencies[i] != 0 && op1.inputDependencies[i]) {
                op1WouldDependOnOp2 = true;
            }
            if (op1.outputDependencies[i] != 0 && op2.outputDependencies[i] != 0) {
                op1op2OutputsDisjoint = false;
            }
        }
        if (!op1.op.isJump() && op2outputIsSuperset && !op2dependsOnOp1) {
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }

        if (op1.op.spec.getName().equals("ld") &&
            op2.op.spec.getName().equals("ld")) {
            if (op1.op.args.get(0).isRegister(code) &&
                op1.op.args.get(1).isRegister(code) &&
                op2.op.args.get(0).isRegister(code) &&
                op2.op.args.get(1).isRegister(code) && 
                op1.op.args.get(0).registerOrFlagName.equals(op2.op.args.get(1).registerOrFlagName) &&
                op2.op.args.get(0).registerOrFlagName.equals(op1.op.args.get(1).registerOrFlagName)) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
            
            if (!op2dependsOnOp1 && 
                op1.op.args.get(0).isRegister(code) &&
                op2.op.args.get(0).isRegister(code)) {
                if (op1.op.args.get(1).isRegister(code)) {
                    if (op1.op.args.get(1).registerOrFlagName.equals(op2.op.args.get(0).registerOrFlagName)) return true;
                }
                // cannonical order of "ld":
                if (op1.op.args.get(0).registerOrFlagName.compareTo(op2.op.args.get(0).registerOrFlagName) > 0) {
//                    System.out.println("- removed: " + op1.op + "; " + op2.op);
                    return false;
                }
            }
            
            if (op1.op.args.get(0).isRegister(code) &&
                op2.op.args.get(0).isRegister(code) &&
                op1.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                op2.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                op1.op.args.get(0).integerConstant == op1.op.args.get(1).integerConstant) {
                // we should use the register value instead of the constant!
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
        if (op1.op.spec.getName().equals("ld") &&
            op2.op.spec.getName().equals("add") &&
            op1.op.args.get(0).isRegister(code) &&
            op1.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a") &&
            op1.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            op1.op.args.get(1).integerConstant == 0 &&    
            op2.op.args.get(0).isRegister(code) &&
            op2.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a") &&
            op2.op.args.get(1).isRegister(code)) {
            // These sequence of two should just be replaced by a single "ld"
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }
        
        if ((op1.op.spec.getName().equals("and") ||
             op1.op.spec.getName().equals("or")) &&
            op2.op.spec.getName().equals(op1.op.spec.getName()) &&
            (!op1.op.args.get(0).isRegister(code) || !op1.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a")) &&
            (!op2.op.args.get(0).isRegister(code) || !op2.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a"))) {
            // cannonical order of commutative operations:
            if (op1.op.toString().compareTo(op2.op.toString()) > 0) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }        
        
        if (op1.op.spec.getName().equals("ld") &&
            op1.op.args.get(0).isRegister(code) &&
            code.isBase8bitRegister(op1.op.args.get(0).registerOrFlagName) &&
            op1.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            (op2.op.spec.getName().equals("add") ||
             op2.op.spec.getName().equals("adc") ||
             op2.op.spec.getName().equals("sbc")) &&
            op2.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            op1.op.args.get(1).integerConstant == op2.op.args.get(1).integerConstant) {
            // we should just use the register instead!
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }

        if (op1.op.spec.getName().equals("ld") &&
            op1.op.args.get(0).isRegister(code) &&
            code.isBase8bitRegister(op1.op.args.get(0).registerOrFlagName) &&
            op1.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            (op2.op.spec.getName().equals("sub") ||
             op2.op.spec.getName().equals("and") ||
             op2.op.spec.getName().equals("or") ||
             op2.op.spec.getName().equals("xor")) &&
            op2.op.args.get(0).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            op1.op.args.get(1).integerConstant == op2.op.args.get(0).integerConstant) {
            // we should just use the register instead!
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }
        
        // There is no point on zeroing "a", and then add/or/xor/and to it, unless
        // we are going to use the P/V flag (which is only used for conditional jumps/calls):
        if (op1.op.toString().equals("sub a") || op1.op.toString().equals("xor a")) {
            if (!spec.allowedOps.contains("jp") && !spec.allowedOps.contains("jr") && !spec.allowedOps.contains("call") &&
                (op2.op.spec.getName().equals("add") || op2.op.spec.getName().equals("adc") || 
                 op2.op.spec.getName().equals("and") ||
                 op2.op.spec.getName().equals("or") || op2.op.spec.getName().equals("xor")) &&
                op2.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                op2.op.args.get(0).registerOrFlagName.equals("a")) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
            // RLCA/RLA/RRCA/RRA/RLC/RL/RRC/RR with "a" after "sub a" or "xor a" is a no-op:
            // same with SLA/SRA/SRL
            if (op2.op.toString().equals("rlca") ||
                op2.op.toString().equals("rla") ||
                op2.op.toString().equals("rrca") ||
                op2.op.toString().equals("rra") ||
                op2.op.toString().equals("rlc a") ||
                op2.op.toString().equals("rl a") ||
                op2.op.toString().equals("rrc a") ||
                op2.op.toString().equals("sla a") ||
                op2.op.toString().equals("sra a") ||
                op2.op.toString().equals("srl a")) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
        if (op1.op.toString().equals("sub a") || op1.op.toString().equals("xor a") ||
            op1.op.toString().equals("and a") || op1.op.toString().equals("or a")) {
            // carry is cleared here, so sbc is the same as sub:
            if (spec.allowedOps.contains("sub") &&
                op2.op.spec.getName().equals("sbc") &&
                op2.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                op2.op.args.get(0).registerOrFlagName.equals("a")) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
            // carry is cleared here, so adc is the same as add:
            if (spec.allowedOps.contains("add") &&
                op2.op.spec.getName().equals("adc")) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }

            if (spec.allowedOps.contains("sra") &&
                op2.op.spec.getName().equals("srl") &&
                op2.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                op2.op.args.get(0).registerOrFlagName.equals("a")) {                    
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
        
        if (!op2dependsOnOp1 && 
            !op1WouldDependOnOp2 && 
            op1op2OutputsDisjoint &&
            !op1.op.isJump() &&
            !op2.op.isJump()) {
            // general canonical order of instructions that are independent:
            if (op1.op.toString().compareTo(op2.op.toString()) > 0) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
    
        return true;
    }
        
    
    @Override
    public String toString()
    {
        return op.toString();
    }
}

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
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import parser.SourceLine;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;

/**
 *
 * @author santi
 */
public class SBOCandidate {
    public int bytes[] = null;
    public CPUOp op;
    public String opString = null;

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
        opString = c.opString;
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
                prefixStr += c.toString() + ";";
            }
         }
        prefixStr += toString() + ";";
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
            prefixStr += c.toString() + ";";
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
            SequenceFilter filter,
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
        
        if (maxLength == 1) {
            for(SBOCandidate op:allCandidateOps) {
                op.potentialFollowUps = allCandidateOps;
            }
            return true;
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
                if (sequenceMakesSense(sequence, spec, carryFlagDepIdx, filter, code)) {
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
        
//        int count = 0;
//        for(String key:candidateOpsByPrefix.keySet()) {
//            count += candidateOpsByPrefix.get(key).potentialFollowUps.size();
//        }
//        System.out.println("precomputeCandidates (allCandidateOps = "+allCandidateOps.size()+", maxLength = " + maxLength + ") = " + count);
        
        return true;
    }
    
    
    static String sequenceString(List<SBOCandidate> sequence)
    {
        String str = "";
        for(SBOCandidate c:sequence) {
            str += c.toString() + "; ";
        }
        return str.trim();
    }


    static boolean sequenceMakesSense(List<SBOCandidate> sequence, Specification spec, 
                                      int carryFlagDepIdx, SequenceFilter filter, CodeBase code)
    {
        // pair-wise dependencies:
//        for(int i = 0;i<sequence.size()-1;i++) {
//            if (!sequenceMakesSensePair(sequence.get(i), sequence.get(i+1), spec, code)) return false;
//        }
        
        if (filter.filterSequence(sequence, 2)) return false;
        
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
                    (!op1.op.args.get(1).isRegister() || !op1.op.args.get(0).registerOrFlagName.equals(op1.op.args.get(1).registerOrFlagName)) &&
                    (!op2.op.args.get(1).isRegister() || !op2.op.args.get(0).registerOrFlagName.equals(op2.op.args.get(1).registerOrFlagName)) &&
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
    
    
    /*
    static boolean sequenceMakesSensePair(SBOCandidate op1, SBOCandidate op2, Specification spec, CodeBase code)
    {
        // These sequences are already captured below, so, no need to check for them:
        // - ld X, Y; ld X, Z
        // op1, op2 is useless if op2's output is a superseteq of op1's, but op2 does not take any input dependency from op1
        boolean op2outputIsSuperset = true;
        boolean op2dependsOnOp1 = false;
        boolean op1WouldDependOnOp2 = false;
        boolean op1op2OutputsDisjoint = true;
        String op1str = op1.toString();
        String op2str = op2.toString();
        
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
            if (op1.op.args.get(0).isRegister() &&
                op1.op.args.get(1).isRegister() &&
                op2.op.args.get(0).isRegister() &&
                op2.op.args.get(1).isRegister() && 
                op1.op.args.get(0).registerOrFlagName.equals(op2.op.args.get(1).registerOrFlagName) &&
                op2.op.args.get(0).registerOrFlagName.equals(op1.op.args.get(1).registerOrFlagName)) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
            
            if (!op2dependsOnOp1 && 
                op1.op.args.get(0).isRegister() &&
                op2.op.args.get(0).isRegister()) {
                if (op1.op.args.get(1).isRegister()) {
                    if (op1.op.args.get(1).registerOrFlagName.equals(op2.op.args.get(0).registerOrFlagName)) return true;
                }
                // cannonical order of "ld":
                if (op1.op.args.get(0).registerOrFlagName.compareTo(op2.op.args.get(0).registerOrFlagName) > 0) {
//                    System.out.println("- removed: " + op1.op + "; " + op2.op);
                    return false;
                }
            }
            
            if (op1.op.args.get(0).isRegister() &&
                op2.op.args.get(0).isRegister() &&
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
            op1.op.args.get(0).isRegister() &&
            op1.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a") &&
            op1.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
            op1.op.args.get(1).integerConstant == 0 &&    
            op2.op.args.get(0).isRegister() &&
            op2.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a") &&
            op2.op.args.get(1).isRegister()) {
            // These sequence of two should just be replaced by a single "ld"
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }
        
        if ((op1.op.spec.getName().equals("and") ||
             op1.op.spec.getName().equals("or")) &&
            op2.op.spec.getName().equals(op1.op.spec.getName()) &&
            (!op1.op.args.get(0).isRegister() || !op1.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a")) &&
            (!op2.op.args.get(0).isRegister() || !op2.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a"))) {
            // cannonical order of commutative operations:
            if (op1str.compareTo(op2str) > 0) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }        
        
        if (op1.op.spec.getName().equals("ld") &&
            op1.op.args.get(0).isRegister() &&
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
            op1.op.args.get(0).isRegister() &&
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
        if (op1str.equals("sub a") || op1str.equals("xor a")) {
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
            if (op2str.equals("rlca") ||
                op2str.equals("rla") ||
                op2str.equals("rrca") ||
                op2str.equals("rra") ||
                op2str.equals("rlc a") ||
                op2str.equals("rl a") ||
                op2str.equals("rrc a") ||
                op2str.equals("sla a") ||
                op2str.equals("sra a") ||
                op2str.equals("srl a")) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
        if (op1str.equals("sub a") || op1str.equals("xor a") ||
            op1str.equals("and a") || op1str.equals("or a")) {
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
            if (op1str.compareTo(op2str) > 0) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
        
        // other sequences identified to be identical by an automated test:
        if (spec.allowedOps.contains("add") &&
            spec.allowedOps.contains("adc")) {
            if (op1str.equals("adc a, a") &&
                op2str.equals("sbc a, a")) {
                // ignore (equivalent to add a, a; sbc a, a)
                return false;
            } else if (op1str.equals("sli a") &&
                       op2str.equals("sbc a, a")) {
                // ignore (equivalent to add a, a; sbc a, a)
                return false;
            }
        }
        if ((op1.op.spec.getName().equals("add") || op1.op.spec.getName().equals("adc")) && 
            op2.op.spec.getName().equals("ld") &&
            op1.op.args.get(0).isRegister() &&
            op1.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a") &&
            !op1.op.args.get(0).toString().equals(op1.op.args.get(1).toString()) &&
            !op2.op.args.get(0).toString().equals(op1.op.args.get(0).toString()) &&
            op1.op.args.get(1).toString().equals(op2.op.args.get(1).toString())) {
            if (op2.op.args.get(0).is8bitRegister()) {
                // add a, XXX; ld REG, XXX is equivalent to ld REG, XXX; add a, REG
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
            if (op2.op.args.get(0).isRegister() &&
                op2.op.args.get(1).isConstant() && op2.op.args.get(1).integerConstant == 0) {
                // add a, 0; ld REG, 0 is equivalent to ld REG, 0; add a, REG (even if REG is a 16 bit one, for the second instruction, we can use an 8 bit subregisters)
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return false;
            }
        }
        if (op1str.equals("ld a, 0") &&
            op2.op.spec.getName().equals("sub")) {
            // ignore (equivalent to sub a; ...)
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }        
        if ((op1str.equals("xor a") || op1str.equals("sub a")) &&
            op2str.equals("ld a, 0")) {
            return false;
        }
        if ((op2str.equals("xor a") || op2str.equals("sub a")) &&
            op1.op.spec.getName().equals("ld") &&
            op1.op.args.get(0).is8bitRegister() &&
            op1.op.args.get(1).toString().equals("0")) {
            // we can just do: sub a; ld REG, a
            return false;
        }
        if (op1str.equals("ld a, 0") &&
            (spec.allowedOps.contains("sub") || spec.allowedOps.contains("xor")) &&
            !op2.op.dependsOnAnyFlag() &&
            op2.op.overwritesAllFlags()) {
            // we can use xor a or sub a instead
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }
        if (op1.op.spec.getName().equals("ld") &&
            (op2.op.spec.getName().equals("sbc")) &&
            op1.op.args.get(0).isRegister() &&
            op1.op.args.get(1).isRegister() &&
            op1.op.args.get(0).toString().equals(op2.op.args.get(0).toString()) &&
            op1.op.args.get(1).toString().equals(op2.op.args.get(1).toString())) {
            // nonsense
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }
        if (op1.op.spec.getName().equals("ld") &&
            op2.op.spec.getName().equals("sub") &&
            op1.op.args.get(0).toString().equals("a") &&
            op1.op.args.get(1).isRegister() &&
            op1.op.args.get(1).toString().equals(op2.op.args.get(0).toString())) {
            // nonsense
//            System.out.println("- removed: " + op1.op + "; " + op2.op);
            return false;
        }
        if ((op1str.equals("sbc hl, hl") && op1str.equals("sbc a, a")) ||
            (op1str.equals("sbc hl, hl") && op1str.equals("ld a, h")) ||
            (op1str.equals("sbc hl, hl") && op1str.equals("ld a, l"))) {
            // all equivalent to sbc a, a; sbc hl, hl
            return false;
        }
        if (((op1str.equals("add hl, hl") && op2str.equals("sbc hl, hl")) ||
             (op1str.equals("adc hl, hl") && op2str.equals("sbc hl, hl")) ||
             (op1str.equals("sli h") && op2str.equals("sbc hl, hl"))) &&
            spec.allowedOps.contains("sla")) {
            // equivalent to: sla h; sbc hl, hl
            return false;
        }
        if (op1str.equals("sbc hl, hl")) {
            // h = l = 0
            if (op2str.equals("add a, l") ||
                op2str.equals("adc a, l") ||
                op2str.equals("sbc a, l") ||
                op2str.equals("sub l")) {
                return false;
            }
            if (!spec.allowedOps.contains("daa")) {
                if (op2str.equals("adc a, h")) {
                    return false;
                }
            }
        }
        
        if (!spec.allowedOps.contains("daa")) {
            // the "H, N" flags do not matter:
            if (op1str.equals("sbc a, a") &&
                (op2str.equals("adc a, a") || op2str.equals("sbc a, a"))) {
                return false;
            }
            if (!spec.allowedOps.contains("jp") &&
                !spec.allowedOps.contains("ret") &&
                !spec.allowedOps.contains("call")) {
                // the "H, N, P/V" flags do not matter:
                if (op1str.equals("sbc a, a") &&
                    op2str.equals("sra a")) {
                    return false;
                }
                if (op1str.equals("adc hl, hl") &&
                    op2str.equals("ld hl, 0") &&
                    spec.allowedOps.contains("sli")) {
                    // equivalent to sli h; ld hl, 0    
//                    System.out.println("- removed: " + op1.op + "; " + op2.op);
                    return false;
                }
            }
        }
        
        if (spec.searchType == SearchBasedOptimizer.SEARCH_ID_BYTES) {
            // optimize for size:
            if (op1str.equals("ld hl, 0") && op2str.equals("sub a") &&
                spec.allowedOps.contains("sbc")) {
                // we can do "sub a; sbc hl, hl", which is smaller
                return false;
            }
        } else {
            // optimize for ops/speed:
            if (op1str.equals("sub a") && op2str.equals("sbc hl, hl") &&
                spec.allowedOps.contains("sub")) {
                // we can do "ld hl, 0; sub a", which is faster
                return false;
            }            
        }
        return true;
    }
    */
    
    
    static List<SBOCandidate> precomputeCandidateOps(Specification spec, List<CPUOpDependency> allDependencies, CodeBase code,
                                                     SequenceFilter filter, int maxLength, MDLConfig config)
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
        OK - EX/EXX
        - PUSH/POP
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
            if (!precomputeAndOrXorCp(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("inc") ||
            spec.allowedOps.contains("dec")) {
            if (!precomputeIncDec(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("add") ||
            spec.allowedOps.contains("adc") ||
            spec.allowedOps.contains("sub") ||
            spec.allowedOps.contains("sbc")) {
            if (!precomputeAddAdcSubSbc(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("ld")) {
            if (!precomputeLd(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("rlc") ||
            spec.allowedOps.contains("rl") ||
            spec.allowedOps.contains("rrc") ||
            spec.allowedOps.contains("rr") ||
            spec.allowedOps.contains("rlca") ||
            spec.allowedOps.contains("rla") ||
            spec.allowedOps.contains("rrca") ||
            spec.allowedOps.contains("rra")) {
            if (!precomputeRotations(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("sla") ||
            spec.allowedOps.contains("sra") ||
            spec.allowedOps.contains("srl") ||
            spec.allowedOps.contains("sli")) {
            if (!precomputeShifts(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("cpl") ||
            spec.allowedOps.contains("neg")) {
            if (!precomputeNegations(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("bit") ||
            spec.allowedOps.contains("set") ||
            spec.allowedOps.contains("res")) {
            if (!precomputeBits(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("ccf") ||
            spec.allowedOps.contains("sfc")) {
            if (!precomputeCarry(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("jp") ||
            spec.allowedOps.contains("jr") ||
            spec.allowedOps.contains("djnz")) {
            if (!precomputeJumps(candidates, spec, allDependencies, code, config)) return null;
        }
        if (spec.allowedOps.contains("ex")) {
            if (!precomputeEx(candidates, spec, allDependencies, code, config)) return null;
        }
        
        config.debug("allCandidateOps: " + candidates.size());
        
        // Precalculate which instructions can contribute to the solution:
        boolean goalDependencies[] = spec.getGoalDependencies(allDependencies);
        spec.getGoalDependenciesSatisfiedFromTheStart(allDependencies); // precalculate for later
        for(int i = 0;i<allDependencies.size();i++) {
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
        
        // Filter the base candidates to begin with:
        List<SBOCandidate> toDelete = new ArrayList<>();
        for(SBOCandidate c:candidates) {
            List<SBOCandidate> l = new ArrayList<>();
            l.add(c);
            if (filter.filterSequence(l)) {
                toDelete.add(c);
            }
        }
        candidates.removeAll(toDelete);
        
        // Precompute which ops can follow which other ops:
        HashMap<String, SBOCandidate> candidateOpsByPrefix = new HashMap<>();
        SBOCandidate.precomputeCandidates(candidateOpsByPrefix, 
                candidates, allDependencies, spec, code, maxLength, filter, config);
        
        return candidates;
    }
    
    
    static boolean precomputeOp(String line, List<SBOCandidate> candidates, List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
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
    
    
    static boolean precomputeAndOrXorCp(List<SBOCandidate> candidates, Specification spec, 
                                 List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
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
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // constant argument:
            for(Integer constant:spec.allowed8bitConstants) {
                String line = opName + " " + constant;
                if (constant == 0) {
                    if (opName.equals("add") || opName.equals("sub")) continue;
                }                
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
            }
            
            // (hl):
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = opName + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = opName + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = opName + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        }
                    }
                }
            }
        }
        return true;
    }
    

    static boolean precomputeIncDec(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
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
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // (hl):
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = opName + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = opName + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = opName + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        }
                    }
                }        
            }
        }
        return true;
    }
    
    
    static boolean precomputeAddAdcSubSbc(List<SBOCandidate> candidates, Specification spec, 
                                   List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
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
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // constant argument:
            for(Integer constant:spec.allowed8bitConstants) {
                String line = opName + " a," + constant;
                if (constant == 0) {
                    if (opName.equals("add") || opName.equals("sub")) continue;
                }
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // (hl):
            if (spec.allowRamUse) {
                String line = opName + " a,(hl)";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = opName + " a,("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = opName + " a,("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
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
            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
        }
        return true;
    }    
    
    
    static boolean precomputeLd(List<SBOCandidate> candidates, Specification spec, 
                         List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String arg1 : regNames) {
            if (!spec.allowedRegisters.contains(arg1)) continue;
            for(String arg2 : regNames) {
                if (arg1.equals(arg2)) continue;
                if (!spec.allowedRegisters.contains(arg2)) continue;
                String line = "ld " + arg1 + "," + arg2;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = "ld (hl)," + arg1;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
                line = "ld " + arg1 + ",(hl)";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // constant argument:
            for(Integer constant:spec.allowed8bitConstants) {
                String line = "ld " + arg1 + "," + constant;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
            }
            
            // (ix+d) / (iy+d):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = "ld " + arg1 + ",("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                            line = "ld ("+reg+"+"+constant+")," + arg1;
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = "ld " + arg1 + ",("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
                            line = "ld ("+reg+constant+")," + arg1;
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
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
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
        }
        for(String arg1 : regNames3) {
            if (!spec.allowedRegisters.contains(arg1)) continue;
            for(String arg2 : regNames2) {
                if (!spec.allowedRegisters.contains(arg2)) continue;
                String line = "ld " + arg1 + "," + arg2;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
        }
        
        // ld (hl)/ixh/ixl/iyh/iyl,n:
        {
            String args[] = {"ixh", "ixl", "iyh", "iyl"};
            for(String  arg1:args) {
                if (!spec.allowedRegisters.contains(arg1)) continue;
                for(Integer constant:spec.allowed8bitConstants) {
                    String line = "ld " + arg1 + "," + constant;
                    if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
                }
            }
        }
        
        if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
            for(Integer constant:spec.allowed8bitConstants) {
                String line = "ld (hl)," + constant;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
            }
        }
        
        if (spec.allowRamUse) {
            // ld (nn),a/bc/de/hl/ix/iy/sp
            // ld a/bc/de/hl/ix/iy/sp,(nn)
            String args[] = {"a", "bc", "de", "hl", "ix", "iy", "sp"};
            for(String arg:args) {
                if (!spec.allowedRegisters.contains(arg)) continue;
                for(Integer address:spec.allowed16bitConstants) {
                    String line = "ld (" + address + ")," + arg;
                    if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
                    line = "ld "+arg+",(" + address + ")";
                    if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
                }                
            }        
        }

        // ld (ix+o)/(iy+o),n
        if (spec.allowRamUse) {
            for(Integer constant:spec.allowed8bitConstants) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer offset:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = "ld ("+reg+"+"+offset+")," + constant;
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = "ld ("+reg+offset+")," + constant;
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
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
                    if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;                
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
            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
        }
        if (spec.allowRamUse) {
            String otherRamOps[] = {"ld (bc),a", "ld (de),a",
                                    "ld a,(bc)", "ld a,(de)"};
            for(String line:otherRamOps) {
                StringTokenizer st = new StringTokenizer(line, " ,()");
                st.nextToken();
                if (!spec.allowedRegisters.contains(st.nextToken())) continue;
                if (!spec.allowedRegisters.contains(st.nextToken())) continue;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
            }            
        }
        return true;
    }
    
    
    static boolean precomputeRotations(List<SBOCandidate> candidates, Specification spec, 
                                List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        String opNames[] = {"rlc", "rl", "rrc", "rr"};
        String regNames[] = {"a", "b", "c", "d", "e", "h", "l"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            for(String reg : regNames) {
                if (!spec.allowedRegisters.contains(reg)) continue;
                String line = op + " " + reg;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = op + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // (ix+o)/(iy+o):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = op + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = op + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        }
                    }
                }        
            }
        }        
        if (spec.allowedRegisters.contains("a")) {
            String otherOps[] = {"rlca", "rla", "rrca", "rra"};
            for(String line:otherOps) {
                if (!spec.allowedOps.contains(line)) continue;
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
            }
        }
        return true;
    }


    static boolean precomputeShifts(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
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
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                String line = op + " (hl)";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            
            // (ix+o)/(iy+o):
            if (spec.allowRamUse) {
                for(String reg:new String[]{"ix","iy"}) {
                    if (!spec.allowedRegisters.contains(reg)) continue;
                    for(Integer constant:spec.allowedOffsetConstants) {
                        if (constant >= 0) {
                            String line = op + " ("+reg+"+"+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        } else {
                            String line = op + " ("+reg+constant+")";
                            if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                        }
                    }
                }   
            }
        }        
        return true;
    }
    
    
    static boolean precomputeNegations(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        if (!spec.allowedRegisters.contains("a")) return true;

        String opNames[] = {"cpl", "neg"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            if (!precomputeOp(op, candidates, allDependencies, code, config)) return false;
        }        
        return true;
    }
    
    
    static boolean precomputeBits(List<SBOCandidate> candidates, Specification spec, 
                             List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
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
                    if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
                }
                // (hl)
                if (spec.allowRamUse && spec.allowedRegisters.contains("hl")) {
                    String line = op + " " + bit + ",(hl)";
                    if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
                }
                // (ix+o)/(iy+o):
                if (spec.allowRamUse) {
                    for(String reg:new String[]{"ix","iy"}) {
                        if (!spec.allowedRegisters.contains(reg)) continue;
                        for(Integer constant:spec.allowedOffsetConstants) {
                            if (constant >= 0) {
                                String line = op + " " + bit +",("+reg+"+"+constant+")";
                                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                            } else {
                                String line = op + " " + bit +", ("+reg+constant+")";
                                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;            
                            }
                        }
                    }
                }
            }
        }
        return true;
    }   
    
    
    static boolean precomputeCarry(List<SBOCandidate> candidates, Specification spec, 
                            List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        String opNames[] = {"ccf", "scf"};
        for(String op: opNames) {
            if (!spec.allowedOps.contains(op)) continue;
            if (!precomputeOp(op, candidates, allDependencies, code, config)) return false;
        }
        return true;
    }   
    
    
    static boolean precomputeJumps(List<SBOCandidate> candidates, Specification spec, 
                            List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        if (spec.allowedOps.contains("jp")) {
            for(String flag:new String[]{"", "z,", "po,", "pe,", "p,", "nz,", "nc,", "m,", "c,"}) {
                String line = "jp " + flag + "$";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
            if (!precomputeOp("jp hl", candidates, allDependencies, code, config)) return false;
            if (!precomputeOp("jp ix", candidates, allDependencies, code, config)) return false;
            if (!precomputeOp("jp iy", candidates, allDependencies, code, config)) return false;
        }
        if (spec.allowedOps.contains("jr")) {
            for(String flag:new String[]{"", "z,", "nz,", "nc,", "c,"}) {
                String line = "jr " + flag + "$";
                if (!precomputeOp(line, candidates, allDependencies, code, config)) return false;
            }
        }
        if (spec.allowedOps.contains("djnz")) {
            if (!precomputeOp("djnz $", candidates, allDependencies, code, config)) return false;
        }
        return true;
    }
    
    
    static boolean precomputeEx(List<SBOCandidate> candidates, Specification spec, 
                                List<CPUOpDependency> allDependencies, CodeBase code, MDLConfig config)
    {
        if (spec.allowedRegisters.contains("de") &&
            spec.allowedRegisters.contains("hl")) {
            if (!precomputeOp("ex de,hl", candidates, allDependencies, code, config)) return false;
        }
        if (spec.allowRamUse && 
            spec.allowedRegisters.contains("sp")) {
            if (spec.allowedRegisters.contains("hl")) {
                if (!precomputeOp("ex (sp),hl", candidates, allDependencies, code, config)) return false;
            }
            if (spec.allowedRegisters.contains("ix")) {
                if (!precomputeOp("ex (sp),ix", candidates, allDependencies, code, config)) return false;
            }
            if (spec.allowedRegisters.contains("iy")) {
                if (!precomputeOp("ex (sp),iy", candidates, allDependencies, code, config)) return false;
            }
        }
        if (spec.allowGhostRegisters) {
            if (spec.allowedRegisters.contains("af")) {
                if (!precomputeOp("ex af,af'", candidates, allDependencies, code, config)) return false;
            }
            if (spec.allowedRegisters.contains("bc") &&
                spec.allowedRegisters.contains("de") &&
                spec.allowedRegisters.contains("hl")) {
                if (!precomputeOp("exx", candidates, allDependencies, code, config)) return false;
            }
        }
        return true;
    }    
    
    
    @Override
    public String toString()
    {
        if (opString == null) opString = op.toString();
        return opString;
    }
}

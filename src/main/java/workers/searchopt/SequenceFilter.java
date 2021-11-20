/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOpDependency;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceFile;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import parser.SourceLine;
import util.Resources;

/**
 *
 * @author santi
 */
public class SequenceFilter {
    MDLConfig config;
    Specification spec = null;
    List<CPUOpDependency> allDependencies = null;
    
    HashMap<String, List<SequenceEquivalence>> equivalences = new HashMap<>();
    
    public SequenceFilter(MDLConfig a_config) 
    {
        config = a_config;
    }
    
    
    public void setSpecification(Specification a_spec)
    {
        spec = a_spec;
        allDependencies = spec.precomputeAllDependencies();
    }
    
    
    public boolean filterSequence(List<SBOCandidate> sequence, int maxSubSequenceSize)
    {
        List<SBOCandidate> subseq = new ArrayList<>();
        for(int size = 1;size<=maxSubSequenceSize;size++) {
            for(int i = 0;i<sequence.size();i++) {
                subseq.clear();
                for(int j = 0;j<size;j++) {
                    if (sequence.size() <= i+j) break;
                    subseq.add(sequence.get(i+j));
                    if (filterSequence(subseq)) return true;
                }
            }
        }
        
        return false;
    }    
    
    
    public boolean filterSequence(List<SBOCandidate> sequence)
    {
        if (sequence.size() == 2) {
            if (filter2SequenceHardcoded(sequence.get(0), sequence.get(1))) {
//                System.out.println("filter2SequenceHardcoded filtered: " + sequence);
                return true;
            }
        }
        List<SequenceEquivalence> l = equivalences.get(SequenceEquivalence.getKeySBO(sequence));
        if (l == null) return false;
                
        for(SequenceEquivalence eq:l) {
            if (eq.flagsToIgnoreCheck(spec) &&
                eq.alternativeIsBetter(spec.searchType) &&
                eq.alternativeAllowed(spec)) {
//                System.out.println("filtered " + eq.key1 + " which is worse than " + eq.key2);
//                System.out.println("    " + eq.time1 + ", " + eq.size1 + "  vs  " + eq.time2 + ", " + eq.size2);
                return true;
            }
        }
        return false;
    }
    
    
    public boolean filterSequence(String sequenceString, int maxSubSequenceSize) throws Exception
    {
        List<SBOCandidate> seq = parseSequence(sequenceString, allDependencies, config);
        return filterSequence(seq, maxSubSequenceSize);
    }

    
    public boolean filterSequence(String sequenceString, Specification spec) throws Exception
    {
        List<SBOCandidate> seq = parseSequence(sequenceString, allDependencies, config);
        return filterSequence(seq);
    }
    
    
    public boolean filter2SequenceHardcoded(SBOCandidate op1, SBOCandidate op2)
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
            // If both are "ld" to memory, we are not sure if they are actually redundant:
            if (!op1.op.isLdToMemory() || !op2.op.isLdToMemory()) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return true;
            }
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
                return true;
            }
            
            if (!op2dependsOnOp1 && 
                op1.op.args.get(0).isRegister() &&
                op2.op.args.get(0).isRegister()) {
                if (op1.op.args.get(1).isRegister()) {
                    if (op1.op.args.get(1).registerOrFlagName.equals(op2.op.args.get(0).registerOrFlagName)) return false;
                }
                // cannonical order of "ld":
                if (op1.op.args.get(0).registerOrFlagName.compareTo(op2.op.args.get(0).registerOrFlagName) > 0) {
//                    System.out.println("- removed: " + op1.op + "; " + op2.op);
                    return true;
                }
            }
            
            if (op1.op.args.get(0).isRegister() &&
                op2.op.args.get(0).isRegister() &&
                op1.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                op2.op.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                op1.op.args.get(0).integerConstant == op1.op.args.get(1).integerConstant) {
                // we should use the register value instead of the constant!
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return true;
            }
        }
        
        if ((op1.op.spec.getName().equals("and") ||
             op1.op.spec.getName().equals("or")) &&
            op2.op.spec.getName().equals(op1.op.spec.getName()) &&
            (!op1.op.args.get(0).isRegister() || !op1.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a")) &&
            (!op2.op.args.get(0).isRegister() || !op2.op.args.get(0).registerOrFlagName.equalsIgnoreCase("a"))) {
            // cannonical order of commutative operations:
            if (op1str.compareTo(op2str) > 0) {
//                System.out.println("- removed: " + op1.op + "; " + op2.op);
                return true;
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
                return true;
            }
        }
        
        return false;
    }
    
    
    
    public boolean loadEquivalences(String fileName) throws Exception
    {
        BufferedReader br = Resources.asReader(fileName);
        
        while(true) {
            String line = br.readLine();
            if (line == null) break;
            String columns[] = line.split("\t");
            String seq1 = columns[0];
            String seq2 = columns[1];
            String flagStr = (columns.length >= 3 ? columns[2]:"");
            
            // parse sequences:
            List<SBOCandidate> l1 = parseSequence(seq1, allDependencies, config);
            if (l1 == null) {
                config.error("SequenceFilter: cannot parse " + seq1);
                return false;
            }
            List<SBOCandidate> l2 = parseSequence(seq2, allDependencies, config);
            if (l2 == null) {
                config.error("SequenceFilter: cannot parse " + seq2);
                return false;
            }
            List<String> flags = new ArrayList<>();
            for(String flag:flagStr.split(",")) {
                flag = flag.trim();
                if (!flag.isEmpty()) {
                    flags.add(flag);
                }
            }
            
            addEquivalence(l1, l2, flags);
        }
        
//        System.out.println("equivalences: " + equivalences.size());
        return true;
    }
    
    
    public static List<SBOCandidate> parseSequence(String sequence, List<CPUOpDependency> allDependencies, MDLConfig config) throws Exception
    {
        String lines[] = sequence.split(";");
 
        CodeBase code = new CodeBase(config);
        SourceFile f = new SourceFile("dummy", null, null, code, config);
        List<SBOCandidate> ops = new ArrayList<>();
        
        for(int i = 0;i<lines.length;i++) {
            String line = lines[i];
            List<String> tokens = config.tokenizer.tokenize(line);
            List<CodeStatement> l = config.lineParser.parse(tokens, new SourceLine(line, f, i), f, null, code, config);
            for(CodeStatement s:l) {
                SBOCandidate c = new SBOCandidate(s, allDependencies, code, config);
                ops.add(c);
            }
        }
        
        return ops;
    }
    
    
    public void addEquivalence(List<SBOCandidate> l1, List<SBOCandidate> l2, List<String> flagsToIgnore)
    {
        SequenceEquivalence eq1 = new SequenceEquivalence(l1, l2, flagsToIgnore);
        if (eq1.alternativeCanBeBetter()) {
            addEquivalence(eq1.getKey(), eq1);
        }
        SequenceEquivalence eq2 = new SequenceEquivalence(l2, l1, flagsToIgnore);
        if (eq2.alternativeCanBeBetter()) {
            addEquivalence(eq2.getKey(), eq2);
        }
    }
    
    
    public void addEquivalence(String key, SequenceEquivalence eq)
    {
        List<SequenceEquivalence> l = equivalences.get(key);
        if (l == null) {
            l = new ArrayList<>();
            equivalences.put(key, l);
        }
        l.add(eq);
    }
}

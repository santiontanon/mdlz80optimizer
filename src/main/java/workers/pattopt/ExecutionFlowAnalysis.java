/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers.pattopt;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.SourceFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author santi
 */
public class ExecutionFlowAnalysis {
    public static class StatementTransition {
        public static final int STANDARD_TRANSITION = 0;
        public static final int CALL_TRANSITION = 1;
        public static final int RET_TRANSITION = 2;
        public static final int POST_CALL_TRANSITION = 3;  // Special case of the transition of a "call" op, to the next instruction, after returning from the call
        public static final int NON_STANDARD_STACK_TRANSITION = 4;  // When stack is modified directly
        
        public CodeStatement s;
        public int transitionType = STANDARD_TRANSITION;
        
        public StatementTransition(CodeStatement a_s, int a_tt)
        {
            s = a_s;
            transitionType = a_tt;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof StatementTransition) {
                StatementTransition st = (StatementTransition)o;
                return s == st.s && transitionType == st.transitionType;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.s);
            hash = 97 * hash + this.transitionType;
            return hash;
        }
    }
    
    
    MDLConfig config;
    CodeBase code;
    
    HashMap<CodeStatement, List<StatementTransition>> forwardTable = null;
    HashMap<CodeStatement, List<StatementTransition>> reverseTable = null;
    HashSet<CodeStatement> statementsWithExportedLabels = null;
    
    boolean stop_analysis_on_untracked_rst = false;
        
    
    public ExecutionFlowAnalysis(CodeBase a_code, MDLConfig a_config) {
        config = a_config;
        code = a_code;
    }


    public void clear()
    {
        forwardTable = null;
        reverseTable = null;
        statementsWithExportedLabels = null;
    }


    public List<StatementTransition> nextOpExecutionStatements(CodeStatement s)
    {
        return nextOpExecutionStatements(s.source.getStatements().indexOf(s), s.source);
    }
    
    
    public List<StatementTransition> nextOpExecutionStatements(int index, SourceFile f)
    {
        List<StatementTransition> open = new ArrayList<>();
        List<Pair<CodeStatement, List<CodeStatement>>> next = f.nextExecutionStatements(index, true, new ArrayList<>(), code);
        if (next == null) return null;

        List<StatementTransition> nextOpStatements = new ArrayList<>();
        for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
            if (tmp.getRight() == null) {
                open.add(new StatementTransition(tmp.getLeft(), StatementTransition.NON_STANDARD_STACK_TRANSITION));
            } else if (tmp.getRight().isEmpty()) {
                open.add(new StatementTransition(tmp.getLeft(), StatementTransition.STANDARD_TRANSITION));
            } else {
                open.add(new StatementTransition(tmp.getLeft(), StatementTransition.CALL_TRANSITION));
            }
        }
        while(!open.isEmpty()) {
            StatementTransition s = open.remove(0);
            if (s.s.op != null) {
                if (!nextOpStatements.contains(s)) {
                    nextOpStatements.add(s);
                }
            } else {
                next = f.nextExecutionStatements(s.s, true, new ArrayList<>(), code);
                if (next == null) return null;
                for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
                    open.add(new StatementTransition(tmp.getLeft(), s.transitionType));
                }
            }
        }
        return nextOpStatements;
    }
    
    
    public Pair<HashMap<CodeStatement, List<StatementTransition>>,
                HashMap<CodeStatement, List<StatementTransition>>> generateForwardAndReverseJumpTables()
    {
        if (forwardTable != null) {
            return Pair.of(forwardTable, reverseTable);
        }
        forwardTable = new HashMap<>();
        reverseTable = new HashMap<>();
        
        for(SourceFile f:code.getSourceFiles()) {
            for(int i = 0;i<f.getStatements().size();i++) {
                if (f.getStatements().get(i).op != null) {
                    CodeStatement s_i = f.getStatements().get(i);
                    if (s_i.op.isRet()) continue;
                    List<StatementTransition> next = nextOpExecutionStatements(i, f);
                    if (next == null) {
                        if (s_i.op.isRst()) {
                            if (!stop_analysis_on_untracked_rst) {
                                continue;
                            }
                        }
                        config.error("Cannot determine the next statements after: " + s_i);
                        return null;
                    }
                    forwardTable.put(s_i, next);
                    for(StatementTransition st:next) {
                        if (!reverseTable.containsKey(st.s)) {
                            reverseTable.put(st.s, new ArrayList<>());
                        }
                        StatementTransition s_i_transition = new StatementTransition(s_i, st.transitionType);
                        if (!reverseTable.get(st.s).contains(s_i_transition)) {
                            reverseTable.get(st.s).add(s_i_transition);
                        }
                    }
                    if ((s_i.op.isCall() || s_i.op.isRst()) && !s_i.op.isConditional()) {
                        // Since "rets" are ignored, there is no way for the
                        // analyzer to know how to get to the statement right
                        // after a conditional call/rst. So, we hardcode it here:
                        CodeStatement s_i_next = s_i.source.getNextStatementTo(s_i, code);
                        if (s_i_next.op == null) {
                            List<StatementTransition> s_i_next_l = nextOpExecutionStatements(s_i_next);
                            if (s_i_next_l == null || s_i_next_l.size() > 1) {
                                config.error("findRetDestinations: failed to find the next op after " + s_i_next.fileNameLineString());
                                s_i_next = null;
                            } else {
                                s_i_next = s_i_next_l.get(0).s;
                            }
                        }
                        if (s_i_next != null) {
                            if (!reverseTable.containsKey(s_i_next)) {
                                reverseTable.put(s_i_next, new ArrayList<>());
                            }
                            StatementTransition s_i_transition = new StatementTransition(s_i, StatementTransition.POST_CALL_TRANSITION);
                            if (!reverseTable.get(s_i_next).contains(s_i_transition)) {
                                reverseTable.get(s_i_next).add(s_i_transition);
                            }
                        }
                    }
                }
            }
        }
        
        return Pair.of(forwardTable, reverseTable);
    }
                
                
    public HashMap<CodeStatement, List<StatementTransition>> findAllRetDestinations()
    {
        HashMap<CodeStatement, List<StatementTransition>> forwardTableOnlyRets = new HashMap<>();
                
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.op != null && s.op.isRet()) {
                    config.debug("findAllRetDestinations: found ret: " + s);
                    List<StatementTransition> destinations = findRetDestinations(s);
                    if (destinations == null) {
                        config.debug("findAllRetDestinations: null for " + s);
                    } else {
                        forwardTableOnlyRets.put(s, destinations);
                        config.debug("findAllRetDestinations: " + destinations.size() + " for " + s);
                        for(StatementTransition d:destinations) {
                            config.debug("    " + d.s);
                        }
                    }
                }
            }
        }
        
        return forwardTableOnlyRets;
    }
                    
                       
    public List<StatementTransition> findRetDestinations(CodeStatement s)
    {
        if (s.op == null || !s.op.isRet()) {
            config.error("allPossibleReturnStatementsForRet called for " + s);
            return null;
        }

        // make sure the tables are generated:
        generateForwardAndReverseJumpTables();
        
        if (forwardTable.containsKey(s)) return forwardTable.get(s);
        if (!reverseTable.containsKey(s)) return null;
        
        List<StatementTransition> possibleDestinations = new ArrayList<>();
        
        HashSet<CodeStatement> closed = new HashSet<>();
        List<StatementTransition> open = new ArrayList<>();
        open.addAll(reverseTable.get(s));
        while(!open.isEmpty()) {
            StatementTransition st2 = open.remove(0);
            // If the ret goes through a label that is exported, then we play
            // conservative and cancel the analysis:
            if (isExported(st2.s)) {
                config.debug("An exported label was crossed, canceling analysis of ret: " + s.fileNameLineString());
                return null;
            }
            closed.add(st2.s);
            if (st2.transitionType == StatementTransition.NON_STANDARD_STACK_TRANSITION) {
                config.debug("SP modified directly, canceling analysis of ret: " + s.fileNameLineString());
                // SP modified directly, cancel analysis!
                return null;
            }
//            if ((st2.s.op.spec.getName().equalsIgnoreCase("inc") ||
//                 st2.s.op.spec.getName().equalsIgnoreCase("dec")) &&
//                st2.s.op.args.get(0).isRegister("sp")) {
//                config.debug("SP modified directly, canceling analysis of ret: " + s.fileNameLineString());
//                // SP modified directly, cancel analysis!
//                return null;
//            }
//            if (st2.s.op.isLd() && st2.s.op.args.get(0).isRegister("sp")) {
//                // SP modified directly, cancel analysis!
//                config.debug("SP modified directly, canceling analysis of ret: " + s.fileNameLineString());
//                return null;
//            }
//            if (st2.s.op.spec.getName().equalsIgnoreCase("ex") &&
//                st2.s.op.args.get(0).isSPIndirection()) {
//                // return address modified directly, cancel analysis!
//                config.debug("Stack content modified directly, canceling analysis of ret: " + s.fileNameLineString());
//                return null;
//            }
            if (st2.transitionType == StatementTransition.CALL_TRANSITION && 
                (st2.s.op.isCall() || st2.s.op.isRst())) {
                CodeStatement s2_next = st2.s.source.getNextStatementTo(st2.s, code);
                if (s2_next != null) {
                    if (s2_next.op == null) {
                        // Find the next op:
                        List<StatementTransition> s2_next_op_l = nextOpExecutionStatements(s2_next);
                        if (s2_next_op_l == null || s2_next_op_l.size() > 1) {
                            config.error("findRetDestinations: failed to find the next op after " + s.fileNameLineString());
                        } else {
                            possibleDestinations.add(new StatementTransition(s2_next_op_l.get(0).s, StatementTransition.RET_TRANSITION));
                        }
                    } else {
                        possibleDestinations.add(new StatementTransition(s2_next, StatementTransition.RET_TRANSITION));
                    }
                }
            } else {
                // keep going backwards:
                List<StatementTransition> prev_l = reverseTable.get(st2.s);
                if (prev_l == null) {
                    config.debug("Cannot determine execution flow, canceling analysis of ret: " + s.fileNameLineString());
                    config.debug("    Statement MDL could not determine flow was: " + st2.s.fileNameLineString());
                    return null;
                }
                for(StatementTransition prev:prev_l) {
                    if (!closed.contains(prev.s) && !open.contains(prev)) {
                        open.add(prev);
                    }
                }
            }
        }
        
        forwardTable.put(s, possibleDestinations);
        return possibleDestinations;
    }
    
    
    public boolean isExported(CodeStatement a_s)
    {
        if (config.dialectParser == null) return false;
        if (statementsWithExportedLabels == null) {
            statementsWithExportedLabels = new HashSet<>();
            for(SourceFile f:code.getSourceFiles()) {
                for(CodeStatement s:f.getStatements()) {
                    if (s.label != null && config.dialectParser.labelIsExported(s.label)) {
                        if (s.op != null) {
                            statementsWithExportedLabels.add(s);
                        } else {
                            List<StatementTransition> s_next_op_l = nextOpExecutionStatements(s);
                            if (s_next_op_l != null) {
                                for(StatementTransition st:s_next_op_l) {
                                    statementsWithExportedLabels.add(st.s);
                                }
                            }
                        }
                    }
                }
            }
            
            config.debug("isExported:");
            for(CodeStatement s:statementsWithExportedLabels) {
                config.debug("    " + s);
            }
        }
        return statementsWithExportedLabels.contains(a_s);
    }
}

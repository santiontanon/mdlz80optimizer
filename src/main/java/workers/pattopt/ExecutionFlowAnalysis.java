/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers.pattopt;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import util.Resources;

/**
 *
 * @author santi
 */
public class ExecutionFlowAnalysis {
    public static class StatementTransition {
        public static final int STANDARD_TRANSITION = 0;
        public static final int PUSH_CALL_TRANSITION = 1;
        public static final int POP_RET_TRANSITION = 2;
        public static final int POST_CALL_TRANSITION = 3;  // Special case of the transition of a "call" op, to the next instruction, after returning from the call
        public static final int POST_CALL_TO_REGISTERTRANSITION = 4;  // Special case of the transition of a "push/jp reg" op, to the next instruction, after returning from the "call"
        public static final int NON_STANDARD_STACK_TRANSITION = 5;  // When stack is modified directly
        
        public CodeStatement s;
        public int transitionType = STANDARD_TRANSITION;
        public Object valuePushed = null;  // Could be an Integer/Expression/CodeStatement
        
        public StatementTransition(CodeStatement a_s, int a_tt, Object a_vp)
        {
            s = a_s;
            transitionType = a_tt;
            valuePushed = a_vp;
        }
        
        @Override
        public boolean equals(Object o) {
            // We ignore "valuePushed"
            if (o instanceof StatementTransition) {
                StatementTransition st = (StatementTransition)o;
                return s == st.s && transitionType == st.transitionType;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            // We ignore "valuePushed"
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.s);
            hash = 97 * hash + this.transitionType;
            return hash;
        }
    }
    
    
    public class ExecutionFlowNode {
        public CodeStatement s;
        public int stackSize = 0;
        
        public ExecutionFlowNode(CodeStatement a_s, int a_stackSize) {
            s = a_s;
            stackSize = a_stackSize;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ExecutionFlowNode)) return false;
            ExecutionFlowNode efn = (ExecutionFlowNode)o;
            if (!s.equals(efn.s)) {
                return false;
            }
            return stackSize == stackSize;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.s);
            hash = 97 * hash + this.stackSize;
            return hash;
        }
    }
    
    
    MDLConfig config;
    CodeBase code;
    
    HashMap<CodeStatement, List<StatementTransition>> forwardTable = null;
    HashMap<CodeStatement, List<StatementTransition>> reverseTable = null;
    HashSet<CodeStatement> statementsWithExportedLabels = null;
    HashSet<CodeStatement> statementsInsideJumpTables = null;
    HashMap<CodeStatement, List<Expression>> statementsThatCallJumpTableJump = new HashMap<>();
    List<Pattern> jumpTablePatterns = new ArrayList<>();
    
    boolean stop_analysis_on_untracked_rst = false;
        
    
    public ExecutionFlowAnalysis(CodeBase a_code, MDLConfig a_config) {
        config = a_config;
        code = a_code;
        loadPatterns("data/jumptablepatterns.txt");
    }
    
    
    public void reset()
    {
        forwardTable = null;
        reverseTable = null;
        statementsWithExportedLabels = null;
        statementsInsideJumpTables = null;
        statementsThatCallJumpTableJump = new HashMap<>();
    }
    
    
    void loadPatterns(String fileName) 
    {
        config.debug("Loading jumptable patterns from " + fileName);
        
        try (BufferedReader br = Resources.asReader(fileName)) {
            String patternString = "";
            while(true) {
                String line = br.readLine();
                if (line == null) {
                    // Load the last pattern:
                    if (!patternString.equals("")) {
                        jumpTablePatterns.add(new Pattern(patternString, config));
                    }
                    break;
                }
                line = line.trim();
                // ignore comments:
                if (config.tokenizer.isSingleLineComment(line)) continue;

                if (line.equals("")) {
                    if (!patternString.equals("")) {
                        jumpTablePatterns.add(new Pattern(patternString, config));
                        patternString = "";
                    }
                } else {
                    if (line.startsWith("include")) {
                        List<String> tokens = config.tokenizer.tokenize(line);
                        if (tokens.size()>=2) {
                            String name = tokens.get(1);
                            if (config.tokenizer.isString(name)) {
                                // include another pattern file:
                                name = name.substring(1, name.length()-1);
                                String path = config.lineParser.pathConcat(FilenameUtils.getFullPath(fileName), name);
                                loadPatterns(path);
                            } else {
                                config.error("Problem loading patterns in line: " + line);
                            }
                        } else {
                            config.error("Problem loading patterns in line: " + line);
                        }
                    } else {
                        patternString += line + "\n";
                    }
                }
            }
        } catch (Exception e) {
            config.error("ExecutionFlowAnalysis: error initializing jumptable patterns! " + e.getMessage());
        }
    }


    public void clear()
    {
        forwardTable = null;
        reverseTable = null;
        statementsWithExportedLabels = null;
        statementsInsideJumpTables = null;
    }


    /*
    - Returns the possible next statements executed after a given one
    - if we have "call/rst" statements, the "next" statements would be the 
      ones being called (not the ones we will return to after the call)
    */
    public List<StatementTransition> nextOpExecutionStatements(CodeStatement s, List<CodeStatement> stack)
    {
        return nextOpExecutionStatements(s.source.getStatements().indexOf(s), s.source, stack);
    }
    
    
    public List<StatementTransition> nextOpExecutionStatements(int index, SourceFile f,  List<CodeStatement> stack)
    {
        CodeStatement a_s = f.getStatements().get(index);
        List<StatementTransition> open = new ArrayList<>();
        List<Pair<CodeStatement, List<CodeStatement>>> next = f.nextExecutionStatements(index, true, stack, code);
        if (next == null && a_s.op != null && a_s.op.isJump()) {
            next = identifyRegisterJumpTargetLocations(a_s);
        }
        if (next == null) return null;

        List<StatementTransition> nextOpStatements = new ArrayList<>();
        for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
            if (a_s.op != null) {
                if (a_s.op.isPop()) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.POP_RET_TRANSITION, null));
                } else if (a_s.op.isRet() && tmp.getRight().size() < stack.size()) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.POP_RET_TRANSITION, null));
                } else if (tmp.getRight() == null) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.NON_STANDARD_STACK_TRANSITION, null));
                } else if (tmp.getRight().size() == stack.size()) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.STANDARD_TRANSITION, null));
                } else if (tmp.getRight().size() > stack.size()) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.PUSH_CALL_TRANSITION, tmp.getRight().get(0)));
                } else {
                    config.error("Something went wrong in ExecutionFlowAnalysis.nextOpExecutionStatements. Please report this bug.");
                    return null;
                }
            } else if (a_s.type == CodeStatement.STATEMENT_NONE) {
                open.add(new StatementTransition(tmp.getLeft(), StatementTransition.STANDARD_TRANSITION, null));
            }
        }
        while(!open.isEmpty()) {
            StatementTransition st = open.remove(0);
            if (st.s.op != null) {
                if (!nextOpStatements.contains(st)) {
                    nextOpStatements.add(st);
                }
            } else {
                next = st.s.source.nextExecutionStatements(st.s, true, new ArrayList<>(), code);
                if (next == null) return null;
                for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
                    open.add(new StatementTransition(tmp.getLeft(), st.transitionType, st.valuePushed));
                }
            }
        }
        return nextOpStatements;
    }
    
    
    public Pair<HashMap<CodeStatement, List<StatementTransition>>,
                HashMap<CodeStatement, List<StatementTransition>>> generateForwardAndReverseTables()
    {
        if (forwardTable != null) {
            return Pair.of(forwardTable, reverseTable);
        }
        forwardTable = new HashMap<>();
        reverseTable = new HashMap<>();
        statementsInsideJumpTables = new HashSet<>();
        // Some statements cannot be resolved until the end:
        List<Pair<SourceFile, Integer>> jumpToRegisterStatementsToCheckAtTheEnd = new ArrayList<>();
        
        for(SourceFile f:code.getSourceFiles()) {
            for(int i = 0;i<f.getStatements().size();i++) {
                if (f.getStatements().get(i).op != null) {
                    CodeStatement s_i = f.getStatements().get(i);
                    if (s_i.op.isRet()) {
                        continue;
                    }
                    List<StatementTransition> next = nextOpExecutionStatements(i, f, new ArrayList<>());
                    if (next == null) {
                        if (s_i.op.isRst()) {
                            if (!stop_analysis_on_untracked_rst) {
                                continue;
                            }
                        }
                        if (s_i.op.isJumpToRegister()) {
                            jumpToRegisterStatementsToCheckAtTheEnd.add(Pair.of(f, i));
                        } else {
                            if (config.allowCallsToNonDefinedSymbolsInExecutionFlowAnalysis) {
                                if (s_i.op.isCall()) {
                                    // ok: this is just probably calling a function that is not defined
                                } else if (s_i.op.isJump()) {
                                    // ok: this is jumping to a function that is not defined (e.g. tail recursion).
                                    //     So, we just ignore it.
                                } else {
                                    config.error("Cannot determine the next statements after: " + s_i.fileNameLineString());
                                    return null;                                    
                                }                                
                            } else {
                                config.error("Cannot determine the next statements after: " + s_i.fileNameLineString());
                                return null;
                            }
                        }
                    } else {
                        if (!generateForwardAndReverseTablesForStatement(s_i, next)) {
                            return null;
                        }
                    }
                }
            }
        }
        
        for(Pair<SourceFile, Integer> f_i:jumpToRegisterStatementsToCheckAtTheEnd) {
            SourceFile f = f_i.getLeft();
            int i = f_i.getRight();
            CodeStatement s_i = f.getStatements().get(i);
            List<StatementTransition> next = nextOpExecutionStatements(i, f, null);
            if (next == null) {
                next = forwardTable.get(s_i);
            }
            if (next == null) {
                config.warn("Cannot determine the next statements after: " + s_i.fileNameLineString());
//                return null;
//            } else {
//                if (!generateForwardAndReverseTablesForStatement(s_i, next)) {
//                    return null;
//                }
            }
        }
        
        /*
        // DEBUG code (remember to comment out after testing):
        System.out.println("Forward/Reverse tables:");
        for(SourceFile f:code.getSourceFiles()) {
            for(int i = 0;i<f.getStatements().size();i++) {
                if (f.getStatements().get(i).op != null) {
                    CodeStatement s = f.getStatements().get(i);
                    System.out.println(s.fileNameLineString() + ": " + s);
                    System.out.println("    Forward:");
                    List<StatementTransition> fl = forwardTable.get(s);
                    if (fl != null) {
                        for(StatementTransition t:fl) {
                            System.out.println("        " + t.s.fileNameLineString() + ": " + t.s);
                        }
                    }
                    System.out.println("    Reverse:");
                    fl = reverseTable.get(s);
                    if (fl != null) {
                        for(StatementTransition t:fl) {
                            System.out.println("        " + t.s.fileNameLineString() + ": " + t.s);
                        }
                    }
                }
            }
        }
        */
        return Pair.of(forwardTable, reverseTable);
    }
                
                
    public boolean generateForwardAndReverseTablesForStatement(CodeStatement s_i, List<StatementTransition> next)
    {
        forwardTable.put(s_i, next);
        for(StatementTransition st:next) {
            if (!reverseTable.containsKey(st.s)) {
                reverseTable.put(st.s, new ArrayList<>());
            }
            StatementTransition s_i_transition = new StatementTransition(s_i, st.transitionType, st.valuePushed);
            if (!reverseTable.get(st.s).contains(s_i_transition)) {
                reverseTable.get(st.s).add(s_i_transition);
            }
        }
        if ((s_i.op.isCall() || s_i.op.isRst()) && !s_i.op.isConditional()) {
            // Since "rets" are ignored, there is no way for the
            // analyzer to know how to get to the statement right
            // after a inconditional call/rst. So, we hardcode it here:
            CodeStatement s_i_next = s_i.source.getNextStatementTo(s_i, code);
            s_i_next = getFirstOpStatement(s_i_next);
            if (s_i_next != null) {
                if (!reverseTable.containsKey(s_i_next)) {
                    reverseTable.put(s_i_next, new ArrayList<>());
                }
                StatementTransition s_i_transition = new StatementTransition(s_i, StatementTransition.POST_CALL_TRANSITION, null);
                if (!reverseTable.get(s_i_next).contains(s_i_transition)) {
                    reverseTable.get(s_i_next).add(s_i_transition);
                }
            } else {
                // See if this is a call to a function that implements a jump table with addresses
                // immediately after with a series of "dw" statements:
                s_i_next = s_i.source.getNextStatementTo(s_i, code);
                while(s_i_next != null && s_i_next.isEmptyAllowingComments()) {
                    s_i_next = s_i.source.getNextStatementTo(s_i, code);
                }
                Pair<List<Expression>, List<CodeStatement>> addresses_jps = detectCallToJumpTableJump(s_i, s_i_next, code);
                if (addresses_jps != null) {
                    List<Expression> tableAddresses = addresses_jps.getLeft();
                    List<CodeStatement> finaljps = addresses_jps.getRight();
                    List<CodeStatement> statementsCorrespondingToAddresses = new ArrayList<>();
                    statementsThatCallJumpTableJump.put(s_i, tableAddresses);
                    config.debug("rets of " + tableAddresses + " will return to post call of caller of " + s_i.fileNameLineString());

                    // We now know that the ret statements in the functions pointed by "tableAddresses"
                    // will return to the post_call_transition of the caller of s_i.
                    for(Expression exp:tableAddresses) {
                        if (exp.type == Expression.EXPRESSION_SYMBOL) {
                            SourceConstant cc = code.getSymbol(exp.symbolName);
                            CodeStatement cc_s = cc.definingStatement;
                            cc_s = getFirstOpStatement(cc_s);
                            if (cc_s != null) {
                                statementsCorrespondingToAddresses.add(cc_s);
                            }
                        } else {
                            config.debug("jump table contains a non-label expression, not yet supported");
                            return false;
                        }
                    }

                    // Set the candidate jump labels of the "jp (hl/ix)" statements:
                    for(CodeStatement jp:finaljps) {
                        for(CodeStatement destination:statementsCorrespondingToAddresses) {
                            StatementTransition jp_transition = new StatementTransition(destination, StatementTransition.STANDARD_TRANSITION, null);
                            StatementTransition reverse_transition = new StatementTransition(jp, StatementTransition.STANDARD_TRANSITION, null);
                            if (!forwardTable.containsKey(jp)) {
                                forwardTable.put(jp, new ArrayList<>());
                            }
                            if (!forwardTable.get(jp).contains(jp_transition)) {
                                forwardTable.get(jp).add(jp_transition);
                            }                                        
                            if (!reverseTable.containsKey(destination)) {
                                reverseTable.put(destination, new ArrayList<>());
                            }
                            if (!reverseTable.get(destination).contains(reverse_transition)) {
                                reverseTable.get(destination).add(reverse_transition);
                            }                                        
                        }
                    }

                    // Set add the destinations of the ret statements to the reverse table:
//                                for(StatementTransition t: reverseTable.get(s_i)) {
//                                    CodeStatement caller_to_jump_table = t.s;
//                                    if (t.s.op != null && t.s.op.isCall()) {
//                                        for(StatementTransition t2:forwardTable.get(caller_to_jump_table)) {
//                                            if (t2.transitionType == StatementTransition.POST_CALL_TRANSITION) {
//                                                if (!reverseTable.containsKey(t2.s)) {
//                                                    reverseTable.put(t2.s, new ArrayList<>());
//                                                }
//                                                // ret_transition: ...
//                                                if (!reverseTable.get(t2.s).contains(ret_transition)) {
//                                                    reverseTable.get(t2.s).add(ret_transition);
//                                                }
//                                            }
//                                        }
//                                    } else {
//                                        config.debug("entry to the 'call to jump_table' method was not a call: not supported.");
//                                        return null;
//                                    }
//                                }

                } else {
                    config.debug("generateForwardAndReverseTables: failed to find the next op after " + s_i.fileNameLineString());
                }
            }
        } else if (s_i.op.isJump() && !s_i.op.isConditional()) {
            // See if this is a "call to register" (ld reg, address; push reg; jp ...)
            CodeStatement retDestination = callToRegisterReturnAddress(s_i);
            if (retDestination != null) {
                config.debug("generateForwardAndReverseTables: 'call to register' ("+s_i+") return statement: " + retDestination);
                if (!reverseTable.containsKey(retDestination)) {
                    reverseTable.put(retDestination, new ArrayList<>());
                }
                StatementTransition s_i_transition = new StatementTransition(s_i, StatementTransition.POST_CALL_TO_REGISTERTRANSITION, null);
                if (!reverseTable.get(retDestination).contains(s_i_transition)) {
                    reverseTable.get(retDestination).add(s_i_transition);
                }
            }
        }   
        return true;
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
        generateForwardAndReverseTables();
        
        if (forwardTable.containsKey(s)) return forwardTable.get(s);
        if (!reverseTable.containsKey(s)) {
            return null;
        }
        if (isExported(s)) {
            config.debug("An exported label was crossed, canceling analysis of ret: " + s.fileNameLineString());
            return null;
        }
        
        List<StatementTransition> possibleDestinations = new ArrayList<>();
        
        HashSet<ExecutionFlowNode> closed = new HashSet<>();
        List<Pair<ExecutionFlowNode, StatementTransition>> open = new ArrayList<>();
        for(StatementTransition st: reverseTable.get(s)) {
            open.add(Pair.of(new ExecutionFlowNode(s, 0), st));
        }
        while(!open.isEmpty()) {
            Pair<ExecutionFlowNode, StatementTransition> tmp = open.remove(0);
            ExecutionFlowNode efn2 = tmp.getLeft();
            StatementTransition st2 = tmp.getRight();
//            config.debug("- efn2: " + efn2.s + "  (stack: " + efn2.stackSize + ")");
//            config.debug("- st2: " + st2.transitionType + " (" + st2.valuePushed + ")  ->  " + st2.s);
            // Ignore statements in jump tables:
            if (statementsInsideJumpTables.contains(st2.s)) continue;
            // If the ret goes through a label that is exported, then we play
            // conservative and cancel the analysis:
            if (isExported(st2.s)) {
                config.debug("An exported label was crossed, canceling analysis of ret: " + s.fileNameLineString());
                return null;
            }
            if (!closed.contains(efn2)) {
                closed.add(efn2);
            }
            // Apply the transition to the ExecutionFlowNode:
            efn2 = new ExecutionFlowNode(st2.s, efn2.stackSize);
            boolean keepGoing = false;
            switch(st2.transitionType) {
                case StatementTransition.PUSH_CALL_TRANSITION:
                    efn2.stackSize -= 1;
                    if (efn2.stackSize < 0) {
                        // We found the place to ret to!
                        if (st2.valuePushed instanceof CodeStatement) {
                            CodeStatement s2_next = (CodeStatement)st2.valuePushed;                            
                            if (s2_next != null) {
                                if (s2_next.op == null) {
                                    // Find the next op:
                                    List<StatementTransition> s2_next_op_l = nextOpExecutionStatements(s2_next, null);
                                    if (s2_next_op_l == null || s2_next_op_l.size() > 1) {
                                        config.error("findRetDestinations: failed to find the next op after " + s.fileNameLineString());
                                    } else {
                                        StatementTransition t = new StatementTransition(s2_next_op_l.get(0).s, StatementTransition.POP_RET_TRANSITION, null);
                                        if (!possibleDestinations.contains(t)) {
                                            possibleDestinations.add(t);
                                        }
                                    }
                                } else {
                                    StatementTransition t = new StatementTransition(s2_next, StatementTransition.POP_RET_TRANSITION, null);
                                    if (!possibleDestinations.contains(t)) {
                                        possibleDestinations.add(t);
                                    }
                                }
                            }
                        } else {
                            config.debug("We cannot determine return address, canceling analysis of ret" + s.fileNameLineString());
                            config.debug("    The instruction that pushed the place to return to is: " + st2.s.fileNameLineString());
                            return null;
                        }
                    } else {
                        keepGoing = true;
                    }
                    break;
                case StatementTransition.POP_RET_TRANSITION:
                    efn2.stackSize += 1;
                    keepGoing = true;
                    break;
                case StatementTransition.POST_CALL_TO_REGISTERTRANSITION:
                    // There will be a "push" just before, so, we need to pre-adjust the stack to be ready
                    efn2.stackSize += 1;
                    keepGoing = true;
                    break;
                case StatementTransition.NON_STANDARD_STACK_TRANSITION:
                    config.debug("SP modified directly, canceling analysis of ret: " + s.fileNameLineString());
                    config.debug("    the cause was: " + st2.s.fileNameLineString());
                    return null;
                default:
                    keepGoing = true;
                    break;
            }
            if (keepGoing) {
                // keep going backwards:
                List<StatementTransition> prev_l = reverseTable.get(efn2.s);                
                if (prev_l == null) {
                    config.debug("Cannot determine execution flow, canceling analysis of ret: " + s.fileNameLineString());
                    config.debug("    Statement MDL could not determine flow was: " + efn2.s.fileNameLineString() + ": " + efn2.s);
                    return null;
                }
                for(StatementTransition st: prev_l) {
                    ExecutionFlowNode efn3 = new ExecutionFlowNode(efn2.s, efn2.stackSize);
                    Pair<ExecutionFlowNode, StatementTransition> pair3 = Pair.of(efn3, st);
                    if (!closed.contains(efn3) && !open.contains(pair3)) {
                        open.add(pair3);
                    }
                }
            }
        }
        
        if (s.op.isConditional()) {
            // Add a regular transition to the next statement:
            List<Pair<CodeStatement, List<CodeStatement>>> next = s.source.immediatelyNextExecutionStatements(s.source.getStatements().indexOf(s), new ArrayList<>(), code);
            if (next.size() == 1) {
                for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
                    possibleDestinations.add(new StatementTransition(tmp.getLeft(), StatementTransition.STANDARD_TRANSITION, null));
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
                            List<StatementTransition> s_next_op_l = nextOpExecutionStatements(s, null);
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
    
    
    /*
    - The input statement "s" should be jp hl / jp ix / jp iy
    - This method tries to find the possible locations to where the instruction can jump (and their stacks)
    - Usually this is a jump table, so, the method tries to find the jump table, and get the labels from it.
    */
    public List<Pair<CodeStatement, List<CodeStatement>>> identifyRegisterJumpTargetLocations(CodeStatement s)
    {
        if (s.comment != null && s.comment.contains(config.PRAGMA_JUMPTABLE)) {
            String tokens[] = s.comment.split(" ");
            int idx = -1;
            for(int j = 0;j<tokens.length;j++) {
                if (tokens[j].equals(config.PRAGMA_JUMPTABLE)) {
                    idx = j + 1;
                    break;
                }
            }
            if (idx > 0 && tokens.length > idx) {
                SourceConstant cc = code.getSymbol(tokens[idx]);
                List<CodeStatement> jumpTables = new ArrayList<>();
                List<CodeStatement> stack = new ArrayList<>();
                jumpTables.add(cc.definingStatement);
                return destinationsInJumpTables(jumpTables, stack);
            }
        }

        for(Pattern p:jumpTablePatterns) {
            int n_statements = p.pattern.size();
            int start_index = s.source.getStatements().indexOf(s);
            for(int i = 0;i<n_statements - 1;i++) {
                start_index--;
                while(start_index > 0 && s.source.getStatements().get(start_index).op == null) {
                    start_index--;
                }
            }
            if (start_index < 0) continue;
            PatternMatch match = p.match(start_index, s.source, code, true, true, null);
            if (match != null) {
                // We have identified a jump table:
                List<CodeStatement> jumpTables = new ArrayList<>();
                List<CodeStatement> stack = new ArrayList<>();
                if (p.tags.contains("table-popped")) {
                    // We need to find where the table is:
                    List<CodeStatement> jumpTableCalls = getCallStatementsAssociatedWithPop(s.source.getStatements().get(start_index));
                    if (jumpTableCalls == null) return null;
                    for(CodeStatement call:jumpTableCalls) {
                        CodeStatement next = call.source.getNextStatementTo(call, code);
                        if (next != null) {
                            next = getFirstOpStatement(next);
                            jumpTables.add(next);
                        } else {
                            return null;
                        }
                    }
                } else {
                    SourceConstant cc = getJumpTableSymbolFromExpression(match.variables.get("?const_table"));
                    if (cc != null) {
                        jumpTables.add(cc.definingStatement);

                        Expression stack_content = match.variables.get("?const_stack");
                        if (stack_content != null) {
                            if (stack_content.type == Expression.EXPRESSION_SYMBOL) {
                                SourceConstant cc2 = code.getSymbol(stack_content.symbolName);
                                stack.add(cc2.definingStatement);
                            } else {
                                config.debug("We do not know how to identify the return address from the jump table, skipping.");
                                return null;
                            }
                        }
                    }
                }
                if (jumpTables.isEmpty()) return null;
                config.debug("identifyRegisterJumpTargetLocations: potential Jump tables: " + jumpTables);

                return destinationsInJumpTables(jumpTables, stack);
            }
        }
        
        return null;
    }
    
    
    public List<Pair<CodeStatement, List<CodeStatement>>>  destinationsInJumpTables(
            List<CodeStatement> jumpTables,
            List<CodeStatement> stack)
    {
        List<Pair<CodeStatement, List<CodeStatement>>> destinations = new ArrayList<>();
        // Identify the list of target destinations in the jump table:
        // Possible patterns:
        // dw label1, label2, ...
        // jp label1; jp label2; ...
        // jp label1; nop; jp label2; nop; jp label3; ...
        HashSet<CodeStatement> statementsInsideThisJumpTable = new HashSet<>();
        String jump_table_style = null;  // "dw", "jp", "jp-nop"
        int state = 0;
        for(CodeStatement jumpTable:jumpTables) {
            CodeStatement s2 = jumpTable;
            while(s2 != null) {
                if (s2.type == CodeStatement.STATEMENT_CPUOP) {
                    if (jump_table_style == null || jump_table_style.equals("jp") || jump_table_style.equals("jp-nop")) {
                        if (jump_table_style != null && jump_table_style.equals("jp-nop")) {
                            if (state == 1) {
                                // expecting a "nop" or a "db 0:
                                if (s2.op.isNop()) {
                                    statementsInsideThisJumpTable.add(s2);
                                    state = 0;
                                } else {
                                    break;
                                }
                            } else {
                                // expecting a "jp":
                                if (s2.op.spec.opName.equalsIgnoreCase("jp") && ! s2.op.isConditional()) {
                                    statementsInsideThisJumpTable.add(s2);
                                    CodeStatement target = statementValueOfLabelExpression(s2.op.args.get(0), code);
                                    if (target == null) return null;
                                    target = getFirstOpStatement(target);
                                    if (target == null) return null;
                                    destinations.add(Pair.of(target, stack));
                                    state = 1;
                                }
                            }
                        } else {
                            if (s2.op.spec.opName.equalsIgnoreCase("jp") && ! s2.op.isConditional()) {
                                statementsInsideThisJumpTable.add(s2);
                                jump_table_style = "jp";
                                CodeStatement target = statementValueOfLabelExpression(s2.op.args.get(0), code);
                                if (target == null) return null;
                                target = getFirstOpStatement(target);
                                if (target == null) return null;
                                destinations.add(Pair.of(target, stack));
                            } else if (s2.op.isNop()) {
                                if (destinations.size() == 1) {
                                    jump_table_style = "jp-nop";
                                    state = 0;
                                } else {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                } else if (s2.type == CodeStatement.STATEMENT_DATA_BYTES) {
                    boolean ok = false;
                    if (jump_table_style != null) {
                        if (jump_table_style.equals("jp-nop") && state == 1) {
                            ok = true;
                        } else if (jump_table_style.equals("jp")) {
                            if (statementsInsideThisJumpTable.size() == 1) {
                                jump_table_style = "jp-nop";
                                state = 1;
                                ok = true;
                            }
                        }
                    }
                    if (ok) {
                        // expecting a "nop" or a "db 0:
                        if (s2.data.size() == 1 &&
                            s2.data.get(0).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                            s2.data.get(0).integerConstant == 0) {
                            statementsInsideThisJumpTable.add(s2);
                            state = 0;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }

                } else if (s2.type == CodeStatement.STATEMENT_DATA_WORDS) {
                    if (jump_table_style == null || jump_table_style.equals("dw")) {
                        jump_table_style = "dw";
                        for(Expression exp: s2.data) {
                            CodeStatement target = statementValueOfLabelExpression(exp, code);
                            if (target == null) return null;
                            target = getFirstOpStatement(target);
                            if (target == null) return null;
                            destinations.add(Pair.of(target, stack));
                        }
                    } else {
                        break;
                    }
                } else if (s2 == jumpTable && s2.type == CodeStatement.STATEMENT_NONE) {
                    // this case is ok
                } else if (!s2.isEmptyAllowingComments()) {
                    break;
                }
                s2 = s2.source.getNextStatementTo(s2, code);
            }
        }

        config.debug("identifyRegisterJumpTargetLocations: destinations" + destinations);
        if (!destinations.isEmpty()) {
            for(CodeStatement jts:statementsInsideThisJumpTable) {
                if (!statementsInsideJumpTables.contains(jts)) {
                    statementsInsideJumpTables.add(jts);
                }
            }
        }

        return destinations;
    }

    
    SourceConstant getJumpTableSymbolFromExpression(Expression exp)
    {
        if (exp.type == Expression.EXPRESSION_SYMBOL) {
            return code.getSymbol(exp.symbolName);
        } else if (exp.type == Expression.EXPRESSION_DIV) {
            Expression left = exp.args.get(0);
            Expression right = exp.args.get(1);
            if (left.type == Expression.EXPRESSION_SYMBOL &&
                right.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                return code.getSymbol(left.symbolName);
            }
        }
        return null;
    }

    
    CodeStatement getFirstOpStatement(CodeStatement s) {
        if (s.op != null) return s;
        List<StatementTransition> s_l = nextOpExecutionStatements(s, null);
        if (s_l == null || s_l.size() != 1) {
            return null;
        } else {
            return s_l.get(0).s;
        }
    }
    
    
//    CodeStatement getFirstDataStatement(CodeStatement s) {
//        if (s.data != null) return s;
//
//        int index = s.source.getStatements().indexOf(s);
//        if (s.source.getStatements().size() > index + 1) {
//            return s.source.getStatements().get(index + 1);
//        }
//
//        return null;
//    }
        
    
    public List<CodeStatement> getKPreviousOps(CodeStatement s, int k)
    {
        List<CodeStatement> l = new ArrayList<>();

        while(l.size() < k) {
            List<StatementTransition> prev_s_l = reverseTable.get(s);
            if (prev_s_l == null || prev_s_l.size() != 1) {
                return null;
            }
            StatementTransition t = prev_s_l.get(0);
            if (t.transitionType != StatementTransition.STANDARD_TRANSITION) {
                return null;
            }
            s = prev_s_l.get(0).s;
            l.add(0, s);
        }
        
        return l;
    }


    public static CodeStatement statementValueOfLabelExpression(Expression exp, CodeBase code) 
    {
        if (exp.type == Expression.EXPRESSION_SYMBOL) {
            SourceConstant targetLabel = code.getSymbol(exp.symbolName);
            if (targetLabel == null) return null;
            if (targetLabel.exp.type == Expression.EXPRESSION_SYMBOL &&
                targetLabel.exp.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
                return targetLabel.definingStatement;
            }
        }        
        return null;
    }
    
    
    /*
        Detects if "s" contains a "jp" in a sequence of statements like this:
            ld reg1, label
            push reg1
            jp reg2
        In this case, the "jp" is actually a "call" where "label" is the return
        address (usually right after "jp reg2", but it does not matter).
    */
    public CodeStatement callToRegisterReturnAddress(CodeStatement s)
    {
        if (s.op == null || !s.op.isJump() || s.op.isConditional()) return null;

        // Check that the previous instruction is a "push":
        List<StatementTransition> prev_t_l = reverseTable.get(s);
        if (prev_t_l == null || prev_t_l.size() != 1) return null;
        CodeStatement s_push = prev_t_l.get(0).s;
        if (s_push.op == null || !s_push.op.isPush()) return null;
        String register = s_push.op.args.get(0).registerOrFlagName;
        // Check that the next previous instruction is an "ld" to the same register that is being pushed:
        List<StatementTransition> prev_t_l2 = reverseTable.get(s_push);
        if (prev_t_l2 == null || prev_t_l2.size() != 1) return null;
        CodeStatement s_ld = prev_t_l2.get(0).s;
        if (s_ld.op == null || !s_ld.op.isLd() || s_ld.op.args.get(0).type!=Expression.EXPRESSION_REGISTER_OR_FLAG) return null;
        if (!register.equals(s_ld.op.args.get(0).registerOrFlagName)) return null;
        // Get the destination label (the value assigned to the register), if we can figure it out:
        CodeStatement destination = ExecutionFlowAnalysis.statementValueOfLabelExpression(s_ld.op.args.get(1), code);
        if (destination == null) return null;
        return getFirstOpStatement(destination);
    }
    
    
    public List<CodeStatement> getCallStatementsAssociatedWithPop(CodeStatement s)
    {
        List<CodeStatement> l = new ArrayList<>();
        
        List<StatementTransition> prev_t_l = reverseTable.get(s);
        // Stop if we find a transition that is a "call" (and if one is, all must be! (for now)):
        for(StatementTransition t:prev_t_l) {
            if (t.transitionType == StatementTransition.PUSH_CALL_TRANSITION) {
                l.add(t.s);
            } else {
                if (!l.isEmpty()) {
                    return null;
                }
            }
        }
        if (!l.isEmpty()) {
            // Make sure all are "calls":
            for(CodeStatement s2:l) {
                if (s2.op == null || !s2.op.isCall()) {
                    return null;
                }
            }
            return l;
        }
        if (l.isEmpty() &&
            prev_t_l.size() == 1 &&
            prev_t_l.get(0).transitionType == StatementTransition.STANDARD_TRANSITION) {
            return getCallStatementsAssociatedWithPop(prev_t_l.get(0).s);
        }        
        return null;
    }
    
    // Returns:
    // - possible jump targets in the jump table
    // - list of jp hl/ix statements
    public Pair<List<Expression>, List<CodeStatement>> detectCallToJumpTableJump(CodeStatement call, CodeStatement table, CodeBase code)
    {
        config.debug("detectCallToJumpTableJump: '"+call+"', '"+table+"'");
        // Get the table addresses:
        if (table.data == null || table.type != CodeStatement.STATEMENT_DATA_WORDS) return null;
        List<Expression> tableAddresses = new ArrayList<>();
        List<CodeStatement> jpToRegisterStatements = new ArrayList<>();
        while(table != null && table.data != null && table.type == CodeStatement.STATEMENT_DATA_WORDS) {
            tableAddresses.addAll(table.data);
            table = table.source.getNextStatementTo(table, code);
            while(table != null && table.isEmptyAllowingComments()) {
                table = table.source.getNextStatementTo(table, code);
            }
        }
        if (tableAddresses.isEmpty()) return null;
        config.debug("detectCallToJumpTableJump addresses: " + tableAddresses);
        
        // Verify that the 'call' statement jumps to a method that is a jump table call:
        // The condition should be that:
        // - all possible paths have at least one "pop hl" (without any other unpaired stack operation) and end in a "jp hl"
        // - there are no loops
        List<Pair<List<CodeStatement>, List<CodeStatement>>> open = new ArrayList<>();  // Each element is "path, stack"
        CodeStatement jumpTableJumpFunction = statementValueOfLabelExpression(call.op.getTargetJumpExpression(), code);
        List<CodeStatement> l = new ArrayList<>();
        l.add(jumpTableJumpFunction);
        open.add(Pair.of(l, new ArrayList<>()));
        while(!open.isEmpty()) {
            Pair<List<CodeStatement>, List<CodeStatement>> path_stack = open.remove(0);
            List<CodeStatement> path = path_stack.getLeft();
            List<CodeStatement> stack = path_stack.getRight();
            config.debug("path: " + path + ", stack: " + stack.size());
            CodeStatement last = path.get(path.size() - 1);
            List<StatementTransition> next = nextOpExecutionStatements(last, stack);
            if (next == null) {
                config.debug("Cannot determine statement after " + last.fileNameLineString());
                return null;
            }
            for(StatementTransition st:next) {
                // Make sure there are no loops:
                if (path.contains(st.s)) {
                    config.debug("Path contains a loop back to " + st.s.fileNameLineString());
                    return null;
                }
                if (st.s.op.isJumpToRegister()) {
                    // Check that the sequence has one pop without other unpaired stack operation:
                    String jumpRegister = st.s.op.getTargetJumpRegister();
                    boolean good = false;
                    for(CodeStatement s:path) {
                        if (s.op != null && s.op.isRet()) {
                            config.debug("Path contains a ret!");
                            return null;
                        }
                        if (s.op != null && s.op.isPop() && s.op.args.get(0).registerOrFlagName.equals(jumpRegister)) {
                            // Path is good!
                            good = true;
                            break;
                        }
                    }
                    if (!good) {
                        config.debug("Path does not contain a pop of the jump register!");
                        return null;
                    }
                    jpToRegisterStatements.add(st.s);
                } else {
                    List<CodeStatement> path2 = new ArrayList<>();
                    List<CodeStatement> stack2 = new ArrayList<>();
                    path2.addAll(path);
                    path2.add(st.s);
                    stack2.addAll(stack);
                    switch(st.transitionType) {
                        case StatementTransition.PUSH_CALL_TRANSITION:
                            {
                                CodeStatement last_next = last.source.getNextStatementTo(last, code);
                                if (last_next == null) {
                                    config.debug("Cannot determine instruction to be executed after " + last.fileNameLineString());
                                    return null;
                                }
                                last_next = getFirstOpStatement(last_next);
                                if (last_next == null) {
                                    config.debug("Cannot determine instruction to be executed after " + last.fileNameLineString());
                                    return null;
                                }
                                config.debug("stack2.add: " + last_next.fileNameLineString());
                                stack2.add(last_next);
                            }
                            break;
                        case StatementTransition.POP_RET_TRANSITION:
                            if (!stack2.isEmpty()) {
                                stack2.remove(stack2.size() - 1);
                            } else {
                                // We do not error out here, since this will happen for sure, as we
                                // will expect a "pop hl/ix" in each path to pop the return address.
                            }
                            break;
                        case StatementTransition.NON_STANDARD_STACK_TRANSITION:
                            config.debug("Path contains a non standard stack transition!");
                            return null;
                    }
                    open.add(Pair.of(path2, stack2));
                }
            }
        }
        
        config.debug("detectCallToJumpTableJump verified jumpTableJumpFunction");

        return Pair.of(tableAddresses, jpToRegisterStatements);
    }
}

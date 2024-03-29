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
            if (!s.equals(efn.s)) return false;
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
    // HashMap<CodeStatement, List<Expression>> statementsThatCallJumpTableJump = new HashMap<>();
    
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
        statementsInsideJumpTables = null;
    }


    public List<StatementTransition> nextOpExecutionStatements(CodeStatement s)
    {
        return nextOpExecutionStatements(s.source.getStatements().indexOf(s), s.source);
    }
    
    
    public List<StatementTransition> nextOpExecutionStatements(int index, SourceFile f)
    {
        CodeStatement a_s = f.getStatements().get(index);
        List<StatementTransition> open = new ArrayList<>();
        List<Pair<CodeStatement, List<CodeStatement>>> next = f.nextExecutionStatements(index, true, new ArrayList<>(), code);
        if (next == null && a_s.op != null && a_s.op.isJump()) {
            next = identifyRegisterJumpTargetLocations(a_s);
        }
        if (next == null) return null;

        List<StatementTransition> nextOpStatements = new ArrayList<>();
        for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
            if (a_s.op != null) {
                if ((a_s.op.isPop() || a_s.op.isRet())) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.POP_RET_TRANSITION, null));
                } else if (tmp.getRight() == null) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.NON_STANDARD_STACK_TRANSITION, null));
                } else if (tmp.getRight().isEmpty()) {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.STANDARD_TRANSITION, null));
                } else {
                    open.add(new StatementTransition(tmp.getLeft(), StatementTransition.PUSH_CALL_TRANSITION, tmp.getRight().get(0)));
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
                next = f.nextExecutionStatements(st.s, true, new ArrayList<>(), code);
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
                            // TODO: This code is WIP to handle test case "test13b" in ExecutionFlowTest
                            /*
                            // See if this is a call to a function that implements a jump table with addresses
                            // immediately after with a series of "dw" statements:
                            s_i_next = s_i.source.getNextStatementTo(s_i, code);
                            while(s_i_next != null && s_i_next.isEmptyAllowingComments()) {
                                s_i_next = s_i.source.getNextStatementTo(s_i, code);
                            }
                            List<Expression> tableAddresses = detectCallToJumpTableJump(s_i, s_i_next, code);
                            if (tableAddresses != null) {
                                statementsThatCallJumpTableJump.put(s_i, tableAddresses);
                            } else {
                            */
                                config.debug("generateForwardAndReverseTables: failed to find the next op after " + s_i.fileNameLineString());                            
                            // }
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
                }
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
        if (!reverseTable.containsKey(s)) return null;
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
            // Apply the transition to the ExecutionFlowNode:
            efn2.s = st2.s;
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
                                    List<StatementTransition> s2_next_op_l = nextOpExecutionStatements(s2_next);
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
    
    
    /*
    - The input statement "s" should be jp hl / jp ix / jp iy
    - This method tries to find the possible locations to where the instruction can jump (and their stacks)
    - Usually this is a jump table, so, the method tries to find the jump table, and get the labels from it.
    */
    public List<Pair<CodeStatement, List<CodeStatement>>> identifyRegisterJumpTargetLocations(CodeStatement s)
    {
        if (s.op == null || !s.op.isJump()) return null;
        String register = null;
        {
            Expression arg = s.op.args.get(0);
            if (arg.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                register = arg.registerOrFlagName;
            } else if (arg.type == Expression.EXPRESSION_PARENTHESIS &&
                       arg.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                register = arg.args.get(0).registerOrFlagName;
            }
        }
        if (register == null) return null;
        
        // It is a potential jump table based jump:
        config.debug("identifyRegisterJumpTargetLocations: " + s);
        int maxLookBack = 10;
        
        // Go back until we find the instruction that loads "hl" with the base
        // value (we will ignore "add" instructions, but any other instruction
        // that modifies hl will cancel the analysis.
        
        List<Pair<CodeStatement, Integer>> open = new ArrayList<>();
        CodeStatement regLdStatement = null;
        List<StatementTransition> prev_l = reverseTable.get(s);
        if (prev_l != null) {
            for(StatementTransition st:prev_l) {
                open.add(Pair.of(st.s, maxLookBack));        
            }
        }
        while(!open.isEmpty()) {
            Pair<CodeStatement, Integer> next = open.remove(0);
            CodeStatement s2 = next.getLeft();
            int lookback_s2 = next.getRight();
            if (s2.label != null) {
                config.debug("identifyRegisterJumpTargetLocations: label found, search for the jump table start ended.");
                regLdStatement = null;
                break;
            }
            if (next.getRight() <= 0) {
                config.debug("identifyRegisterJumpTargetLocations: maxLookBack reached, search for the jump table start ended.");
                regLdStatement = null;
                break;
            }
            if (s2.op != null) {
                if (s2.op.isCall()) break;
                if (s2.op.modifiesRegister(register)) {
                    if (s2.op.isLd() || s2.op.isPop()) {
                        if (regLdStatement == null) {
                            regLdStatement = s2;
                        } else {
                            if (regLdStatement != s2) {
                                config.debug("identifyRegisterJumpTargetLocations: More than one statement setting the jump table is not supported.");
                                regLdStatement = null;
                                break;
                            }
                        }
                        break;
                    } else if (s2.op.spec.getName().equalsIgnoreCase("ex")) {                        
                        // See if this is the pattern found:
                        //   ld e, (hl)
                        //   inc hl
                        //   ld d, (hl)
                        //   ex de, hl
                        List<CodeStatement> ops = getKPreviousOps(s2, 3);
                        if (ops == null || ops.size() != 3) {
                            regLdStatement = null;
                            break;
                        } else {
                            ops.add(s2);
                            if (ops.get(0).op.toString().equalsIgnoreCase("ld e, (hl)") &&
                                ops.get(1).op.toString().equalsIgnoreCase("inc hl") &&
                                ops.get(2).op.toString().equalsIgnoreCase("ld d, (hl)") &&
                                ops.get(3).op.toString().equalsIgnoreCase("ex de, hl")) {
                                // all good!
                                s2 = ops.get(0);
                                lookback_s2 -= 3;
                            } else {
                                config.debug("identifyRegisterJumpTargetLocations: unsupported case for 'ex de, hl'.");
                                regLdStatement = null;
                                break;                                
                            }
                        }
                        
                    } else if (!s2.op.isAdd()) {
                        config.debug("identifyRegisterJumpTargetLocations: unexpected function modifying the jump table resiger ("+s2.op+"), search for the jump table start ended.");
                        regLdStatement = null;
                        break;
                    }
                }
                List<StatementTransition> prev_s2_l = reverseTable.get(s2);
                if (prev_l != null) {
                    for(StatementTransition st:prev_s2_l) {
                        open.add(Pair.of(st.s, lookback_s2 - 1));        
                    }
                } else {
                    config.debug("identifyRegisterJumpTargetLocations: can't go back at: " + s2);
                    regLdStatement = null;
                    break;
                }
            } else {
                if (!s2.isEmptyAllowingComments()) {
                    config.debug("identifyRegisterJumpTargetLocations: unexpected statement: " + s2);
                    regLdStatement = null;
                    break;
                }                
            }
        }
                
        List<CodeStatement> jumpTables = new ArrayList<>();
        if (regLdStatement != null) {
            if (regLdStatement.op.isLd()) {
                Expression label = regLdStatement.op.args.get(1);
                if (label.type == Expression.EXPRESSION_SYMBOL) {
                    SourceConstant cc = code.getSymbol(label.symbolName);
                    jumpTables.add(cc.definingStatement);
                }
            } else if (regLdStatement.op.isPop()) {
                // Find all possible values that "pop" can assign to the register:
                List<CodeStatement> callStatements = getCallStatementsAssociatedWithPop(regLdStatement);
                if (callStatements == null) {
                    config.debug("identifyRegisterJumpTargetLocations: getCallStatementsAssociatedWithPop returned null");
                    return null;
                }
                for(CodeStatement s2:callStatements) {
                    CodeStatement s2_next = s.source.getNextStatementTo(s2, code);
                    s2_next = getFirstOpStatement(s2_next);
                    if (s2_next == null) {
                        // See if the next statement is data:
                        s2_next = getFirstDataStatement(s2);
                    }
                    if (s2_next == null) {
                        config.error("identifyRegisterJumpTargetLocations: getFirstOpStatement returned null for s2: " + s2);
                        return null;
                    } 
                    jumpTables.add(s2_next);
                }
            } else {
                config.error("identifyRegisterJumpTargetLocations: internal error 1 (please report if you see this)");
                return null;
            }
        } else {
            config.debug("identifyRegisterJumpTargetLocations: could not find regLdStatement.");
        }

        if (jumpTables.isEmpty()) return null;

        config.debug("identifyRegisterJumpTargetLocations: potential Jump tables: " + jumpTables);
        
        // Identify the list of target destinations in the jump table:
        // Possible patterns:
        // dw label1, label2, ...
        // jp label1; jp label2; ...
        // jp label1; nop; jp label2; nop; jp label3; ...
        HashSet<CodeStatement> statementsInsideThisJumpTable = new HashSet<>();
        List<CodeStatement> stack = new ArrayList<>();
        List<Pair<CodeStatement, List<CodeStatement>>> destinations = new ArrayList<>();
        String jump_table_style = null;  // "dw", "jp", "jp-nop"
        int state = 0;
        for(CodeStatement jumpTable:jumpTables) {
            CodeStatement s2 = jumpTable;
            while(s2 != null) {
                if (s2.type == CodeStatement.STATEMENT_CPUOP) {
                    if (jump_table_style == null || jump_table_style.equals("jp") || jump_table_style.equals("jp-nop")) {
                        if (jump_table_style != null && jump_table_style.equals("jp-nop")) {
                            if (state == 1) {
                                // expecting a "nop":
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

    
    CodeStatement getFirstOpStatement(CodeStatement s) {
        if (s.op != null) return s;
        List<StatementTransition> s_l = nextOpExecutionStatements(s);
        if (s_l == null || s_l.size() != 1) {
            return null;
        } else {
            return s_l.get(0).s;
        }
    }
    
    
    CodeStatement getFirstDataStatement(CodeStatement s) {
        if (s.data != null) return s;

        int index = s.source.getStatements().indexOf(s);
        if (s.source.getStatements().size() > index + 1) {
            return s.source.getStatements().get(index + 1);
        }

        return null;
    }
        
    
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
    
    // TODO: This code is WIP to handle test case "test13b" in ExecutionFlowTest
    /*
    // Returns possible jump targets in the jump table
    public List<Expression> detectCallToJumpTableJump(CodeStatement call, CodeStatement table, CodeBase code)
    {
        config.debug("detectCallToJumpTableJump: '"+call+"', '"+table+"'");
        // Get the table addresses:
        if (table.data == null || table.type != CodeStatement.STATEMENT_DATA_WORDS) return null;
        List<Expression> tableAddresses = new ArrayList<>();
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
        List<List<CodeStatement>> open = new ArrayList<>();
        CodeStatement jumpTableJumpFunction = statementValueOfLabelExpression(call.op.getTargetJumpExpression(), code);
        List<CodeStatement> l = new ArrayList<>();
        l.add(jumpTableJumpFunction);
        open.add(l);
        while(!open.isEmpty()) {
            List<CodeStatement> path = open.remove(0);
            System.out.println(path);
            CodeStatement last = path.get(path.size() - 1);
            List<StatementTransition> next = nextOpExecutionStatements(last);
            if (next == null) return null;
            for(StatementTransition st:next) {
                // Make sure there are no loops:
                if (path.contains(st.s)) return null;
                if (st.s.op.isJumpToRegister()) {
                    // Check that the sequence has 
                    String jumpRegister = st.s.op.getTargetJumpRegister();
                    boolean good = false;
                    for(CodeStatement s:path) {
                        if (s.op.isRet()) return null;
                        if (s.op.isPop() && s.op.args.get(0).registerOrFlagName.equals(jumpRegister)) {
                            // Path is good!
                            good = true;
                            break;
                        }
                    }
                    if (!good) return null;
                } else {
                    List<CodeStatement> path2 = new ArrayList<>();
                    path2.addAll(path);
                    path2.add(st.s);
                    open.add(path2);
                }
            }
        }
        
        config.debug("detectCallToJumpTableJump verified jumpTableJumpFunction");

        return tableAddresses;
    }
    */
}

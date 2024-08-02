/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;
import workers.pattopt.ExecutionFlowAnalysis;

public class SourceFile {
    MDLConfig config;

    public String fileName = null;
    public String originalFileName = null;
    List<CodeStatement> statements = new ArrayList<>();
    public SourceFile parent = null;
    public CodeStatement parentInclude = null;
    public CodeBase code = null;

    public SourceFile(String a_fileName, SourceFile a_parent, CodeStatement a_parentInclude, CodeBase a_code, MDLConfig a_config) {
        fileName = a_fileName;
        originalFileName = a_fileName;
        parent = a_parent;
        parentInclude = a_parentInclude;
        code = a_code;
        config = a_config;
    }


    public String getPath() {
        return FilenameUtils.getFullPath(fileName);
    }


    public List<CodeStatement> getStatements()
    {
        return statements;
    }


    public void addStatement(CodeStatement s) {
        statements.add(s);
    }


    public void addStatement(int position, CodeStatement s) {
        statements.add(position, s);
    }


    public void resetAddresses()
    {
        for(CodeStatement s:statements) {
            s.resetAddress();
        }
    }


    public CodeStatement getNextStatementTo(CodeStatement s, CodeBase code)
    {
        int index = statements.indexOf(s);
        if (index == -1) {
            if (!statements.isEmpty()) {
                // assume the statement has not yet been added, so, return the first:
                CodeStatement s2 = statements.get(0);
                if (s2.include != null) {
                    return s2.include.getNextStatementTo(null, code);
                }
            } else {
                return null;
            }
        }
        if (index == statements.size()-1) {
            // look at the instruction after the "include" that included this file:
            if (parent != null) return parent.getNextStatementTo(parentInclude, code);
            return null;
        }
        return statements.get(index+1);
    }


    public CodeStatement getPreviousStatementTo(CodeStatement s, CodeBase code)
    {
        int index = statements.indexOf(s);
        if (index == -1) {
            // assume the statement has not yet been added, so, return the last:
            index = statements.size();
        }
        if (index == 0) {
            // look at the instruction before the "include" that included this file:
            if (parent != null) return parent.getPreviousStatementTo(parentInclude, code);
            return null;
        }
        return statements.get(index-1);
    }
    
    
    public List<CodeStatement> linearizeStatements(CodeBase code)
    {
        List<CodeStatement> l = new ArrayList<>();
        CodeStatement s = getStatements().get(0);;

        while(s != null) {
            while(s.include != null) {
                s = s.include.getStatements().get(0);
            }
            l.add(s);
            s = s.source.getNextStatementTo(s, code);
        }

        return l;
    }


    public Integer sizeInBytes(CodeBase code, boolean withIncludes, boolean withIncbin, boolean withVirtual) {
        return sizeInBytesInternal(code, withIncludes, withIncbin, withVirtual, new ArrayList<>());
    }
    
        
    public Integer sizeInBytesInternal(CodeBase code, boolean withIncludes, boolean withIncbin, boolean withVirtual, List<String> variableStack) {
        int size = 0;
        for (CodeStatement s : statements) {
            Integer s_size = s.sizeInBytesInternal(code, withIncludes, withIncbin, withVirtual, variableStack);
            if (s_size == null) return null;
            if (s_size < 0) {
                config.warn("statement with negative size: " + s_size + " -> " + s);
            }
            size += s_size;
        }
        return size;
    }

    
    // Returns the accumulated time of all the assembler instructions in this
    // file.
    public int[] accumTiming() {
        int time[] = {0,0};
        for (CodeStatement s : statements) {
            if (s.op != null) {
                time[0] += s.op.spec.times[0];
                if (s.op.spec.times.length >= 2) {
                    time[1] += s.op.spec.times[1];
                } else {
                    time[1] += s.op.spec.times[0];
                }
            }
        }
        return time;
    }
    
    
    public String accumTimingString() {
        int time[] = accumTiming();
        if (time[0] == time[1]) {
            return "" + time[0];
        } else {
            return time[0] + "/" + time[1];
        }
    }
    

    // returns <statement, callstack>
    public List<Pair<CodeStatement, List<CodeStatement>>> 
        nextExecutionStatements(CodeStatement s, boolean goInsideInclude,
                                List<CodeStatement> callStack, CodeBase code)
    {
        int index = statements.indexOf(s);
        if (index == -1) {
            config.error("Cannot find statement " + s + " in " + this.fileName + " with " + statements.size() + " statements.");
            return null;
        }
        return nextExecutionStatements(index, goInsideInclude, callStack, code);
    }


    /*
    This function returns "null" when there are some potential next statements that cannot be determined.
    For example, when encountering a "ret", a "jp hl", a "call CONSTANT", where CONSTANT is not a label (could be a system call)
    */
    public List<Pair<CodeStatement, List<CodeStatement>>> 
        nextExecutionStatements(int index, boolean goInsideInclude,
                                List<CodeStatement> callStack, CodeBase code)
    {
        CodeStatement s = statements.get(index);
        switch(s.type) {
            case CodeStatement.STATEMENT_CPUOP:
            {
//                if (s.op.isRst()) {
//                    // not currently suported
//                    return null;
//                }
                List<Pair<CodeStatement, List<CodeStatement>>> next = new ArrayList<>();
                if (s.op.isRet()) {
                    if (callStack != null) {
                        if (!callStack.isEmpty()) {
                            CodeStatement target = callStack.get(callStack.size()-1);
                            if (target != null) {
                                List<CodeStatement> newCallStack = new ArrayList<>();
                                for(int i = 0;i<callStack.size()-1;i++) {
                                    newCallStack.add(callStack.get(i));
                                }
                                next.add(Pair.of(target, newCallStack));
                                if (s.op.isConditional()) {
                                    next.addAll(immediatelyNextExecutionStatements(index, callStack, code));
                                }
                                return next;
                            }
                        } else {
                            // Check if we have this ret in the execution flow table:
                            List<ExecutionFlowAnalysis.StatementTransition> destinations = code.getStatementPossibleDestinations(s);
                            if (destinations != null) {
                                List<CodeStatement> newCallStack = new ArrayList<>();
                                for(int i = 0;i<callStack.size()-1;i++) {
                                    newCallStack.add(callStack.get(i));
                                }
                                for(ExecutionFlowAnalysis.StatementTransition t: destinations) {
                                    if (t.transitionType == ExecutionFlowAnalysis.StatementTransition.POP_RET_TRANSITION) {
                                        next.add(Pair.of(t.s, newCallStack));                            
                                    } else if (s.op.isConditional()) {
                                        if (t.transitionType == ExecutionFlowAnalysis.StatementTransition.STANDARD_TRANSITION) {
                                            next.add(Pair.of(t.s, callStack));                            
                                        }
                                    }
                                }
                                return next;
                            }
                        }
                    }
                    
                    // we don't know where are we going to jump to:
                    return null;
                }

                Expression labelExp = s.op.getTargetJumpExpression();
                SourceConstant label = null;
                if (labelExp != null) {
                    if (labelExp.type == Expression.EXPRESSION_SYMBOL) {
                        label = code.getSymbol(labelExp.symbolName);
                        if (label == null)  {
                            // not all next statements can be determined:
                            return null;
                        }
                    } else if (labelExp.evaluatesToIntegerConstant()) {
                        Integer targetAddress = labelExp.evaluateToInteger(s, code, true);
                        if (targetAddress != null) {
                            // See if there is any label that matches with this value:
                            for(SourceConstant label2:code.symbols.values()) {
                                Object tmp = label2.getValue(code, true);
                                if (tmp != null && tmp instanceof Integer) {
                                    Integer label2Value = (Integer)tmp;
                                    if ((int)label2Value == (int)targetAddress) {
                                        label = label2;
                                        break;
                                    }
                                }
                            }
                        }
                        if (label == null) return null;
                    } else {
                        // not all next statements can be determined:
                        return null;
                    }
                    CodeStatement jumpTargetStatement = null;
                    if (label.isLabel()) {
                        jumpTargetStatement = label.definingStatement;
                    } else {
                        // not all next statements can be determined:
                        return null;
                    }

                    // get target CodeStatement:
                    if (s.op.isConditional()) {
                        next = immediatelyNextExecutionStatements(index, callStack, code);
                    } else {
                        next = new ArrayList<>();
                    }
                    List<CodeStatement> newCallStack = callStack;
                    if (newCallStack != null && (s.op.isCall() || s.op.isRst())) {
                        List<Pair<CodeStatement, List<CodeStatement>>> returnFromCall = immediatelyNextExecutionStatements(index, callStack, code);
                        if (returnFromCall.size() != 1) {
                            config.error("immediatelyNextExecutionStatements returned " + returnFromCall.size() + ", instead of 1!");
                            return null;
                        }
                        newCallStack = new ArrayList<>();
                        newCallStack.addAll(callStack);
                        newCallStack.add(returnFromCall.get(0).getLeft());
                    }
                    next.add(Pair.of(jumpTargetStatement, newCallStack));
                    return next;
                } else {
                    List<CodeStatement> newCallStack = callStack;
                    if (newCallStack != null) {
                        if (s.op.isPush()) {
                            CodeStatement valuePushed = null;
                            // Detect if the value being pushed is a label:
                            if (s.label == null) {
                                int i = index - 1;
                                while(i >= 0) {
                                    CodeStatement prev = statements.get(i);
                                    if (prev.label != null) break;
                                    if (prev.op != null) {
                                        if (prev.op.isLd() && prev.op.modifiesRegister(s.op.args.get(0).registerOrFlagName)) {
                                            // "ld" to the correct register:
                                            valuePushed = ExecutionFlowAnalysis.statementValueOfLabelExpression(prev.op.args.get(1), code);
                                        }
                                        break;
                                    } else if (!prev.isEmptyAllowingComments()) {
                                        break;
                                    }
                                    i--;
                                }
                            }
                            newCallStack = new ArrayList<>();
                            newCallStack.addAll(callStack);
                            newCallStack.add(valuePushed);
                        } else if (s.op.isPop()) {
                            newCallStack = new ArrayList<>();
                            newCallStack.addAll(callStack);
                            if (newCallStack.isEmpty()) {
                                newCallStack = null;
                            } else {
                                newCallStack.remove(newCallStack.size()-1);
                            }
                        } else if (s.op.modifiesStackInNonStandardWay()) {
                            newCallStack = null;
                        }
                    }
                    return immediatelyNextExecutionStatements(index, newCallStack, code);
                }
            }

            case CodeStatement.STATEMENT_INCLUDE:
                // go inside the include:
                if (goInsideInclude && !s.include.statements.isEmpty()) {
                    SourceFile fi = s.include;
                    List<Pair<CodeStatement, List<CodeStatement>>> next = new ArrayList<>();
                    next.add(Pair.of(fi.statements.get(0), callStack));
                    return next;
                } else {
                    // skip the include:
                    return immediatelyNextExecutionStatements(index, callStack, code);
                }

            case CodeStatement.STATEMENT_MACRO:
            case CodeStatement.STATEMENT_MACROCALL:
                throw new IllegalStateException("Macros should have been resolved before optimization!");

            default:
                return immediatelyNextExecutionStatements(index, callStack, code);
        }
    }


    public List<Pair<CodeStatement, List<CodeStatement>>> immediatelyNextExecutionStatements(int index,
            List<CodeStatement> callStack, CodeBase code)
    {
        if (statements.size() > index+1) {
            List<Pair<CodeStatement, List<CodeStatement>>> next = new ArrayList<>();
            next.add(Pair.of(statements.get(index+1), callStack));
            return next;
        }
        // file is over, go up one level:
        if (parentInclude!=null) {
            return parent.nextExecutionStatements(parentInclude, false, callStack, code);
        }
        // we are done, no next!
        return new ArrayList<>();
    }


    public void evaluateAllExpressions(CodeBase code)
    {
        for(CodeStatement s:statements) {
            s.evaluateAllExpressions(code, config);
        }
    }
    
    
    public Integer getStartAddress(CodeBase code)
    {
        // Find the first statement that has some bytes:
        for(CodeStatement s:statements) {
            if (s.sizeInBytes(code, true, true, true) > 0) {
                return s.getAddress(code);
            }
        }
        return null;
    }
}

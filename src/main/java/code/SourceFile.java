/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;

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


    public Integer sizeInBytes(CodeBase code, boolean withIncludes, boolean withIncbin, boolean withVirtual) {
        return sizeInBytesInternal(code, withIncludes, withIncbin, withVirtual, new ArrayList<>());
    }
    
        
    public Integer sizeInBytesInternal(CodeBase code, boolean withIncludes, boolean withIncbin, boolean withVirtual, List<String> variableStack) {
        int size = 0;
        for (CodeStatement s : statements) {
            Integer s_size = s.sizeInBytesInternal(code, withIncludes, withIncbin, withVirtual, variableStack);
            if (s_size == null) return null;
            if (s_size < 0) {
                System.out.println("statement with negative size: " + s_size + " -> " + s);
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
                if (s.op.isRst()) {
                    // not currently suported
                    return null;
                }
                if (s.op.isRet()) {
                    if (callStack != null && !callStack.isEmpty()) {
                        CodeStatement target = callStack.get(callStack.size()-1);
                        if (target != null) {
                            List<CodeStatement> newCallStack = new ArrayList<>();
                            for(int i = 0;i<callStack.size()-1;i++) {
                                newCallStack.add(callStack.get(i));
                            }
                            List<Pair<CodeStatement, List<CodeStatement>>> next = new ArrayList<>();
                            next.add(Pair.of(target, newCallStack));
                            if (s.op.isConditional()) {
                                next.addAll(immediatelyNextExecutionStatements(index, callStack, code));
                            }
                            return next;
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
                    List<Pair<CodeStatement, List<CodeStatement>>> next;
                    if (s.op.isConditional()) {
                        next = immediatelyNextExecutionStatements(index, callStack, code);
                    } else {
                        next = new ArrayList<>();
                    }
                    List<CodeStatement> newCallStack = callStack;
                    if (newCallStack != null && s.op.isCall()) {
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
                            newCallStack = new ArrayList<>();
                            newCallStack.addAll(callStack);
                            newCallStack.add(null);
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
}

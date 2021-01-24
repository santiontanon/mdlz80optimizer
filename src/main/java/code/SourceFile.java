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
    List<SourceStatement> statements = new ArrayList<>();
    public SourceFile parent = null;
    public SourceStatement parentInclude = null;
    public CodeBase code = null;

    public SourceFile(String a_fileName, SourceFile a_parent, SourceStatement a_parentInclude, CodeBase a_code, MDLConfig a_config) {
        fileName = a_fileName;
        parent = a_parent;
        parentInclude = a_parentInclude;
        code = a_code;
        config = a_config;
    }


    public String getPath() {
        return FilenameUtils.getFullPath(fileName);
    }


    public List<SourceStatement> getStatements()
    {
        return statements;
    }


    public void addStatement(SourceStatement s) {
        statements.add(s);
    }


    public void addStatement(int position, SourceStatement s) {
        statements.add(position, s);
    }


    public void resetAddresses()
    {
        for(SourceStatement s:statements) {
            s.resetAddress();
        }
    }


    public SourceStatement getNextStatementTo(SourceStatement s, CodeBase code)
    {
        int index = statements.indexOf(s);
        if (index == -1) {
            // assume the statement has not yet been added, so, return the first:
            SourceStatement s2 = statements.get(0);
            if (s2.include != null) {
                return s2.include.getNextStatementTo(null, code);
            }
        }
        if (index == statements.size()-1) {
            // look at the instruction after the "include" that included this file:
            if (parent != null) return parent.getNextStatementTo(parentInclude, code);
            return null;
        }
        return statements.get(index+1);
    }


    public SourceStatement getPreviousStatementTo(SourceStatement s, CodeBase code)
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
        for (SourceStatement s : statements) {
            Integer s_size = s.sizeInBytesInternal(code, withIncludes, withIncbin, withVirtual, variableStack);
            if (s_size == null) return null;
            size += s_size;
        }
        return size;
    }


    // returns <statement, callstack>
    public List<Pair<SourceStatement, List<SourceStatement>>> 
        nextExecutionStatements(SourceStatement s, boolean goInsideInclude,
                                List<SourceStatement> callStack, CodeBase code)
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
    public List<Pair<SourceStatement, List<SourceStatement>>> 
        nextExecutionStatements(int index, boolean goInsideInclude,
                                List<SourceStatement> callStack, CodeBase code)
    {
        SourceStatement s = statements.get(index);
        switch(s.type) {
            case SourceStatement.STATEMENT_CPUOP:
            {
                if (s.op.isRst()) {
                    // not currently suported
                    return null;
                }
                if (s.op.isRet()) {
                    // we don't know where are we going to jump to:
                    if (callStack != null && !callStack.isEmpty()) {
                        SourceStatement target = callStack.get(callStack.size()-1);
                        if (target != null) {
                            List<SourceStatement> newCallStack = new ArrayList<>();
                            for(int i = 0;i<callStack.size()-1;i++) {
                                newCallStack.add(callStack.get(i));
                            }
                            List<Pair<SourceStatement, List<SourceStatement>>> next = new ArrayList<>();
                            next.add(Pair.of(target, newCallStack));
                            if (s.op.isConditional()) {
                                next.addAll(immediatelyNextExecutionStatements(index, callStack, code));
                            }
                            return next;
                        }
                    }
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
                }
                SourceStatement jumpTargetStatement = null;
                if (label != null && label.isLabel()) {
                    jumpTargetStatement = label.definingStatement;
                } else if (label != null) {
                    // not all next statements can be determined:
                    return null;
                }

                if (jumpTargetStatement != null) {
                    // get target SourceStatement:
                        List<Pair<SourceStatement, List<SourceStatement>>> next;
                    if (s.op.isConditional()) {
                        next = immediatelyNextExecutionStatements(index, callStack, code);
                    } else {
                        next = new ArrayList<>();
                    }
                    List<SourceStatement> newCallStack = callStack;
                    if (newCallStack != null && s.op.isCall()) {
                        List<Pair<SourceStatement, List<SourceStatement>>> returnFromCall = immediatelyNextExecutionStatements(index, callStack, code);
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
                    List<SourceStatement> newCallStack = callStack;
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

            case SourceStatement.STATEMENT_INCLUDE:
                // go inside the include:
                if (goInsideInclude && !s.include.statements.isEmpty()) {
                    SourceFile fi = s.include;
                    List<Pair<SourceStatement, List<SourceStatement>>> next = new ArrayList<>();
                    next.add(Pair.of(fi.statements.get(0), callStack));
                    return next;
                } else {
                    // skip the include:
                    return immediatelyNextExecutionStatements(index, callStack, code);
                }

            case SourceStatement.STATEMENT_MACRO:
            case SourceStatement.STATEMENT_MACROCALL:
                throw new IllegalStateException("Macros should have been resolved before optimization!");

            default:
                return immediatelyNextExecutionStatements(index, callStack, code);
        }
    }


    public List<Pair<SourceStatement, List<SourceStatement>>> immediatelyNextExecutionStatements(int index,
            List<SourceStatement> callStack, CodeBase code)
    {
        if (statements.size() > index+1) {
            List<Pair<SourceStatement, List<SourceStatement>>> next = new ArrayList<>();
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
        for(SourceStatement s:statements) {
            s.evaluateAllExpressions(code, config);
        }
    }
}

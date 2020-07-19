/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import cl.MDLConfig;

public class SourceFile {
    MDLConfig config;

    public String fileName = null;
    List<SourceStatement> statements = new ArrayList<>();
    SourceFile parent = null;
    SourceStatement parentInclude = null;

    public SourceFile(String a_fileName, SourceFile a_parent, SourceStatement a_parentInclude, MDLConfig a_config) {
        fileName = a_fileName;
        parent = a_parent;
        parentInclude = a_parentInclude;
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
        int size = 0;
        for (SourceStatement s : statements) {
            Integer s_size = s.sizeInBytes(code, withIncludes, withIncbin, withVirtual);
            if (s_size == null) return null;
            size += s_size;
        }
        return size;
    }


    public List<SourceStatement> nextStatements(SourceStatement s, boolean goInsideInclude, CodeBase code)
    {
        int index = statements.indexOf(s);
        return nextStatements(index, goInsideInclude, code);
    }


    /*
    This function returns "null" when there are some potential next statements that cannot be determined.
    For example, when encountering a "ret", a "jp hl", a "call CONSTANT", where CONSTANT is not a label (could be a system call)
    */
    public List<SourceStatement> nextStatements(int index, boolean goInsideInclude, CodeBase code)
    {
        SourceStatement s = statements.get(index);
        switch(s.type) {
            case SourceStatement.STATEMENT_CPUOP:
            {
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
                    jumpTargetStatement = label.s;
                } else if (label != null) {
                    // not all next statements can be determined:
                    return null;
                }

                if (jumpTargetStatement != null) {
                    // get target SourceStatement:
                    if (s.op.isConditional()) {
                        List<SourceStatement> next = immediatelyNextStatements(index, code);
                        next.add(jumpTargetStatement);
                        return next;
                    } else {
                        List<SourceStatement> next = new ArrayList<>();
                        next.add(jumpTargetStatement);
                        return next;
                    }
                } else {
                    return immediatelyNextStatements(index, code);
                }
            }

            case SourceStatement.STATEMENT_INCLUDE:
                // go inside the include:
                if (goInsideInclude && !s.include.statements.isEmpty()) {
                    SourceFile fi = s.include;
                    List<SourceStatement> next = new ArrayList<>();
                    next.add(fi.statements.get(0));
                    return next;
                } else {
                    // skip the include:
                    return immediatelyNextStatements(index, code);
                }

            case SourceStatement.STATEMENT_MACRO:
            case SourceStatement.STATEMENT_MACROCALL:
                throw new IllegalStateException("Macros should have been resolved before optimization!");

            default:
                return immediatelyNextStatements(index, code);
        }
    }


    public List<SourceStatement> immediatelyNextStatements(int index, CodeBase code)
    {
        if (statements.size() > index+1) {
            List<SourceStatement> next = new ArrayList<>();
            next.add(statements.get(index+1));
            return next;
        }
        // file is over, go up one level:
        if (parentInclude!=null) {
            return parent.nextStatements(parentInclude, false, code);
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

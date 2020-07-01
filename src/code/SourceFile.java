/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        int idx = fileName.lastIndexOf(File.separator);
        if (idx == -1) {
            return "";
        }
        return fileName.substring(0, idx);
    }

    
    public List<SourceStatement> getStatements()
    {
        return statements;
    }
    
    
    public void addStatement(SourceStatement s) {
        statements.add(s);
    }
    
    
    public void resetAddresses()
    {
        for(SourceStatement s:statements) {
            s.resetAddress();
        }
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
    

    public int sizeInBytes(CodeBase code, boolean withIncludes, boolean withIncbin, boolean withVirtual) {
        int size = 0;
        for (SourceStatement s : statements) {
            size += s.sizeInBytes(code, withIncludes, withIncbin, withVirtual);
        }
        return size;
    }
    
    
    public List<SourceStatement> nextStatements(SourceStatement s, boolean goInsideInclude, CodeBase code) throws Exception
    {
        int index = statements.indexOf(s);
        return nextStatements(index, goInsideInclude, code);
    }


    /*
    This function returns "null" when there are some potential next statements that cannot be determined.
    For example, when encountering a "ret", a "jp hl", a "call CONSTANT", where CONSTANT is not a label (could be a system call)
    */
    public List<SourceStatement> nextStatements(int index, boolean goInsideInclude, CodeBase code) throws Exception
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
                throw new Exception("Macros should have been resolved before optimization!");

            default:
                return immediatelyNextStatements(index, code);
        }
    }

    
    public List<SourceStatement> immediatelyNextStatements(int index, CodeBase code) throws Exception
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
    
}

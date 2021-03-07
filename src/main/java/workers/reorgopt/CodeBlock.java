/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import code.CodeBase;
import code.SourceConstant;
import code.CodeStatement;
import code.Expression;
import code.OutputBinary;
import code.SourceFile;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class CodeBlock {
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_DATA = 1;
    public static final int TYPE_CODE = 2;
    
    
    public String ID = null;
    public int type = TYPE_UNKNOWN;
    public CodeStatement startStatement;
    public OutputBinary output = null;
    public List<CodeStatement> statements = new ArrayList<>();
    public List<CodeBlock> subBlocks = new ArrayList<>();
    
    public SourceConstant label;    // label with which this CodeBlock starts (if any)
    public List<BlockFlowEdge> incoming = new ArrayList<>();
    public List<BlockFlowEdge> outgoing = new ArrayList<>();
    
        
    public CodeBlock(String a_ID, int a_type, CodeStatement a_start)
    {
        ID = a_ID;
        type = a_type;
        startStatement = a_start;
    }


    public CodeBlock(String a_ID, int a_type, CodeStatement a_start, CodeStatement a_end, CodeBase code)
    {
        ID = a_ID;
        type = a_type;
        startStatement = a_start;
        
        addStatementsFromTo(a_start, a_end, code);
        findStartLabel();
    }
        
    
    public final boolean addStatementsFromTo(CodeStatement start, CodeStatement end, CodeBase code)
    {
        int idx = start.source.getStatements().indexOf(start);
        CodeStatement s = start;
        
        while(s != end) {
            if (s == null) return false;
            if (s.include != null) {
                if (addStatementsFromTo(s.include.getStatements().get(0), end, code)) return true;
            } else {
                statements.add(s);
            }
            idx++;
            if (idx<start.source.getStatements().size()) {
                s = start.source.getStatements().get(idx);
            } else {
                return false;
            }
        }
        return end == null;
    }
    
    
    public final boolean findStartLabel()
    {
        label = null;
        for(CodeStatement s:statements) {
            if (s.label != null) {
                label = s.label;
                return true;
            }
            
            if (s.type != CodeStatement.STATEMENT_NONE &&
                s.type != CodeStatement.STATEMENT_CONSTANT) return false;
        }
        
        return false;
    }
    
    
    public CodeStatement getLastCpuOpStatement()
    {
        for(int i = statements.size()-1; i>=0; i--) {
            CodeStatement s = statements.get(i);
            if (s.type == CodeStatement.STATEMENT_CPUOP) return s;

            if (s.type != CodeStatement.STATEMENT_NONE &&
                s.type != CodeStatement.STATEMENT_CONSTANT) return null;
        }
        
        return null;
    }
    
    
    public void resetAddresses()
    {
        for(CodeStatement s:statements) {
            s.resetAddress();;
        }
    }
    
    
    // Checks if this is a function with a single entry point, and a single ret at the end
    // If it is a simpleFunction called only once, it returns the "call/jp/jr" to this function
    // If it is not, it returns null
    public CodeStatement isSimpleFunctionCalledOnce(CodeBase code)
    {
        if (label == null) return null;
        
        CodeStatement call = null;
        

        // see if there is a single "ret" (at the very end), and that
        // there is no jump outside the block:
        int state = 0;  // 0: begin, 1: first ret found
        for(int i = statements.size()-1;i>=0;i--) {
            CodeStatement s = statements.get(i);
            if (s.type != CodeStatement.STATEMENT_CPUOP) continue;
            if (s.op.isRet()) {
                if (s.op.isConditional()) return null;
                if (state == 0) {
                    state = 1;
                } else {
                    return null;    // more than one ret!
                }
            } else {
                if (state == 0) {
                    return null;    // the block does not end with a ret!
                }
            }
            
            if (s.op.isJump()) {
                SourceConstant targetLabel = s.op.getTargetJumpLabel(code);
                if (targetLabel != null && targetLabel.definingStatement != null) {
                    // jump outside the block!
                    if (!statements.contains(targetLabel.definingStatement)) {
                        return null;
                    }
                }
            }
        }        
                
        // find instructions that call/jump to this block:
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.type == CodeStatement.STATEMENT_CPUOP) {
                    Expression exp = s.op.getTargetJumpExpression();
                    if (exp != null && exp.type == Expression.EXPRESSION_SYMBOL &&
                        exp.symbolName.equals(label.name)) {
                        if (call != null) return null;  // called more than once!
                        call = s;
                    }
                }
            }
        }
        
        // see if there are intermediate labels that are jumped to/called externally:
        for(CodeStatement s:statements) {
            if (s.label != null && s.label != label) {
                for(SourceFile f:code.getSourceFiles()) {
                    for(CodeStatement s2:f.getStatements()) {
                        if (s2.type == CodeStatement.STATEMENT_CPUOP) {
                            Expression exp = s2.op.getTargetJumpExpression();
                            if (exp != null && exp.type == Expression.EXPRESSION_SYMBOL &&
                                exp.symbolName.equals(s.label.name)) {
                                if (!statements.contains(s2)) {
                                    // an internal label is called/jumped to externally, this is not a simple function!
                                    return null;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return call;
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import code.CodeBase;
import code.SourceConstant;
import code.CodeStatement;
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
    public List<CodeStatement> statements = new ArrayList<>();
    public List<CodeBlock> subBlocks = new ArrayList<>();
    
    public SourceConstant label;    // label with which ths CodeBlock starts (if any)
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
        
    
    public boolean addStatementsFromTo(CodeStatement start, CodeStatement end, CodeBase code)
    {
        int idx = start.source.getStatements().indexOf(start);
        CodeStatement s = start;
//        System.out.println("addStatementsFromTo: " + start.source.fileName);
//        System.out.println("    start: " + start);
//        System.out.println("    end: " + end);
        
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
    
    
    public boolean findStartLabel()
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
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import code.CodeBase;
import code.SourceStatement;
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
    public SourceStatement startStatement;
    public List<SourceStatement> statements = new ArrayList<>();
    public List<CodeBlock> subBlocks = new ArrayList<>();
    
        
    public CodeBlock(String a_ID, SourceStatement a_start)
    {
        ID = a_ID;
        startStatement = a_start;
    }


    public CodeBlock(String a_ID, SourceStatement a_start, SourceStatement a_end, CodeBase code)
    {
        ID = a_ID;
        startStatement = a_start;
        
        SourceStatement s = a_start;
        while(s != a_end) {
            if (s == null) break;
            statements.add(s);
            s = s.source.getNextStatementTo(s, code);
        }
    }
}

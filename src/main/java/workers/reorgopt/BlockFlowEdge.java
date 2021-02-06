/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

/**
 *
 * @author santi
 */
public class BlockFlowEdge {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_UNCONDITIONAL_JP = 1;
    public static final int TYPE_UNCONDITIONAL_JR = 2;
    public static final int TYPE_CONDITIONAL_JP = 3;
    public static final int TYPE_CONDITIONAL_JR = 4;
    public static final int TYPE_DJNZ = 5;
    
    public CodeBlock source;
    public CodeBlock target;
    public int type;
    public String condition = null;
    
    
    public BlockFlowEdge(CodeBlock a_source, CodeBlock a_target, int a_type, String a_condition)
    {
        source = a_source;
        target = a_target;
        type = a_type;
        condition = a_condition;
    }
}

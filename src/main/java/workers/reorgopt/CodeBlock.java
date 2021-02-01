/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import code.SourceStatement;

/**
 *
 * @author santi
 */
public class CodeBlock {
    public String ID = null;
    public SourceStatement first, last;
    
    public CodeBlock(String a_ID, SourceStatement a_first, SourceStatement a_last)
    {
        ID = a_ID;
        first = a_first;
        last = a_last;
    }
}

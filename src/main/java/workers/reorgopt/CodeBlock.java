/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class CodeBlock {
    public String ID = null;
    public SourceStatement startStatement;
    public List<SourceStatement> statements = new ArrayList<>();
        
    public CodeBlock(String a_ID, SourceStatement a_start)
    {
        ID = a_ID;
        startStatement = a_start;
    }
}

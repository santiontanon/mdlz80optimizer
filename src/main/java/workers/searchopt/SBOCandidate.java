/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import java.util.List;

/**
 *
 * @author santi
 */
public class SBOCandidate {
    public int bytes[] = null;
    public CPUOp op;
    
    public SBOCandidate(CPUOp a_op, CodeBase code, MDLConfig config)
    {
        op = a_op;
        
        // precompile it to bytes:
        List<Integer> tmp = op.assembleToBytes(null, code, config);
        if (tmp == null) {
            config.error("Cannot assemble CPUOp to bytes: " + op);
        } else {
            bytes = new int[tmp.size()];
            for(int i = 0;i<tmp.size();i++) {
                bytes[i] = tmp.get(i);
            }
        }
    }
}

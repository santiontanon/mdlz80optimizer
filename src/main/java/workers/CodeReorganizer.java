/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers;

import cl.MDLConfig;
import code.CodeBase;
import java.util.List;

/**
 *
 * @author santi
 */
public class CodeReorganizer implements MDLWorker {

    MDLConfig config;
    boolean trigger = false;
    
    public CodeReorganizer(MDLConfig a_config)
    {
        config = a_config;
    }
    
    @Override
    public String docString() {
        return "  -ro: (task) runs the reoganizer optimizer.\n";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-ro")) {
            flags.remove(0);
            trigger = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean triggered() {
        return trigger;
    }

    @Override
    public boolean work(CodeBase code) {
        // TODO: ...        
        return true;
    }

    @Override
    public MDLWorker cloneForExecutionQueue() {
        CodeReorganizer w = new CodeReorganizer(config);

        // reset state:
        trigger = false;
        
        return w;
    }
    
}

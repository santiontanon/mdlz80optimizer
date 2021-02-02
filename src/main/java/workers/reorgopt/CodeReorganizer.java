/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import cl.MDLConfig;
import code.CodeBase;
import java.util.ArrayList;
import java.util.List;
import workers.MDLWorker;

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
        // First, find the "top blocks" that we can work with, e.g. "pages in a MegaROM".
        // These are areas of code such that we can move things around inside, but not across.
        List<CodeBlock> topBlocks = new ArrayList<>();
        if (config.dialectParser != null) {
            config.dialectParser.getTopLevelCodeBlocks(code, topBlocks);
        } else {
            if (code.getMain() != null && !code.getMain().getStatements().isEmpty()) {
                CodeBlock top = new CodeBlock("top", code.getMain().getStatements().get(0));
                topBlocks.add(top);
            }
        }
        
        // Within each "top block", identify blocks of code (there might be code mixed with data in each
        // top block), so, we tease appart each of the contiguous areas of code:
        // Note: each time tehre is a directive like "org", we need to start a new block, since
        // we cannot safely assume we can move code that is under one "org" to an area of the code that
        // is under a different "org". We call these the "top code blocks"
        for(CodeBlock topBlock: topBlocks) {
            List<CodeBlock> topCodeBlocks = findTopCodeBlocks(topBlock, code);
            
            for(CodeBlock block: topCodeBlocks) {
                // Within each "top code block", we can now re-organize code at will:
                reorganizeBlock(block, code);
            }
        }
                
        return true;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        CodeReorganizer w = new CodeReorganizer(config);

        // reset state:
        trigger = false;
        
        return w;
    }
    

    private List<CodeBlock> findTopCodeBlocks(CodeBlock block, CodeBase code) {
        List<CodeBlock> blocks = new ArrayList<>();
        
        // ...
        
        return blocks;
    }
    

    private void reorganizeBlock(CodeBlock block, CodeBase code) {
        // ...
    }
    
}

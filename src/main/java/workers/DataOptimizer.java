/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers;

import cl.MDLConfig;
import cl.OptimizationResult;
import code.CodeBase;
import code.CodeStatement;
import code.SourceFile;
import java.util.ArrayList;
import java.util.List;
import workers.reorgopt.CodeBlock;

/**
 *
 * @author santi
 */
public class DataOptimizer implements MDLWorker {
    public static final String DATA_OPTIMIZER_OPTIMIZATIONS_CODE = "DataOptimizer optimizations";
    public static final String DATA_OPTIMIZER_POTENTIAL_BYTES_CODE = "DataOptimizer potential bytes saved";

    MDLConfig config;
    BinaryGenerator binaryGenerator;
    boolean trigger = false;
    
    public DataOptimizer(MDLConfig a_config)
    {
        config = a_config;
        binaryGenerator = new BinaryGenerator(config);
    }

    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-do```: Runs the data optimizer (only provides potential ideas for space saving).\n";
    }

    
    @Override
    public String simpleDocString() {
        return "- ```-do```: Runs the data optimizer (only provides potential ideas for space saving).\n";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-do")) {
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
        OptimizationResult savings = new OptimizationResult();
        savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 0);

        List<CodeBlock> dataBlocks = new ArrayList<>();
        
        // Step 1: identify all the data blocks
        for(SourceFile f:code.getSourceFiles()) {
            CodeStatement start = null;
            for(int i = 0;i<f.getStatements().size();i++) {
                CodeStatement s = f.getStatements().get(i);
                if (start == null) {
                    // We are not inside a datablock:
                    if (s.data != null && !s.data.isEmpty()) {
                        // data start:
                        boolean reachedBeginningOfFileOrOrg = false;
                        if (s.label == null) {
                            int j = i - 1;
                            while(j >= 0) {
                                CodeStatement s2 = f.getStatements().get(j);
                                if (s2.include != null ||
                                    s2.op != null ||
                                    s2.org != null ||
                                    s2.space != null) {
                                    if (s2.org != null) reachedBeginningOfFileOrOrg = true;
                                    break;
                                }
                                if (s2.label != null) {
                                    s = s2;  // start from the last previous label:
                                    break;
                                }
                                j--;
                            }
                            if (j < 0) {
                                reachedBeginningOfFileOrOrg = true;
                            }
                        }
                        if (s.label != null) {
                            start = s;
                        } else {
                            // We do not want to show a warning at the beginning of a file, since the
                            // label might be in a separate file that includes this:
                            if (!reachedBeginningOfFileOrOrg) {
                                config.warn("Data block starts without a label in " + s.sl);
                            }
                        }
                    }
                } else {
                    if (s.include != null || 
                        s.op != null ||
                        s.org != null ||
                        s.space != null) {
                        CodeBlock block = new CodeBlock("data-" + dataBlocks.size(),
                                                        CodeBlock.TYPE_DATA,
                                                        start, s, code);
                        dataBlocks.add(block);
                        start = null;
                    }
                }
            }
            if (start != null) {
                CodeBlock block = new CodeBlock("data-" + dataBlocks.size(),
                                                CodeBlock.TYPE_DATA,
                                                start, null, code);
                dataBlocks.add(block);                
            }
        }
        
        // Step 2: convert each block to bytes:        
        List<List<Integer>> blockBytes = blocksToBytes(dataBlocks, code);
        
        // Step 3: Look for optimization oportunities (coarse-grained):
        // - Check if a block is contained in another
        findBlocksContainedInOthers(dataBlocks, blockBytes, savings);
        // - Check if the end of one datablock is a prefix of another
        // - Check if the reverse of one is contained in another
        // - Check if the reverse of the beginning of one is a prefix of another
        // ...
        
        // Step 4: split the blocks into smaller blocks:
        dataBlocks = splitIntoFineGrainedblocks(dataBlocks, code);
        blockBytes = blocksToBytes(dataBlocks, code);
        

        // Step 5: Look for optimization oportunities (fine-grained):
        findBlocksContainedInOthers(dataBlocks, blockBytes, savings);
        // ...
        
        config.optimizerStats.addSavings(savings);
        return true;
    }
    
    
    public List<CodeBlock> splitIntoFineGrainedblocks(List<CodeBlock> dataBlocks, CodeBase code)
    {
        List<CodeBlock> fineGrainedBlocks = new ArrayList<>();
        
        for(CodeBlock b:dataBlocks) {
            CodeStatement start = null;
            for(CodeStatement s:b.statements) {
                if (s.label != null) {
                    if (start != null) {
                        // new block:
                        CodeBlock block = new CodeBlock("data-fg-" + fineGrainedBlocks.size(),
                                                        CodeBlock.TYPE_DATA,
                                                        start, s, code);
                        fineGrainedBlocks.add(block);
                        start = s;
                    } else {
                        start = s;
                    }
                }
            }
            if (start != null) {
                // new block:
                CodeBlock block = new CodeBlock("data-fg-" + fineGrainedBlocks.size(),
                                                CodeBlock.TYPE_DATA,
                                                start, null, code);
                fineGrainedBlocks.add(block);
            }
        }
        config.debug("DataOptimizer: splitIntoFineGrainedblocks: from " + dataBlocks.size() + " to " + fineGrainedBlocks.size());
        
        return fineGrainedBlocks;
    }
    
    public List<List<Integer>> blocksToBytes(List<CodeBlock> dataBlocks, CodeBase code)
    {
        List<List<Integer>> blockBytes = new ArrayList<>();
        for(CodeBlock db:dataBlocks) {
            List<Integer> bytes = assembleToBytes(db, code);
            if (bytes == null) return null;
            config.debug("DataBlock " + db.startStatement.label.name + ": " +  bytes.size());
            blockBytes.add(bytes);
        }
        return blockBytes;
    }    
    
    public List<Integer> assembleToBytes(CodeBlock db, CodeBase code)
    {
        List<Integer> bytes = new ArrayList<>();
        for(CodeStatement s:db.statements) {
            byte s_bytes[] = binaryGenerator.generateStatementBytes(s, code);
            if (s_bytes != null) {
                for(byte b:s_bytes) {
                    bytes.add((int)b);
                }
            }
        }
        return bytes;
    }
    
    
    public void findBlocksContainedInOthers(List<CodeBlock> dataBlocks, 
                                            List<List<Integer>> blockBytes,
                                            OptimizationResult savings)
    {
        List<CodeBlock> blocksToRemove = new ArrayList<>();
        for(int i = 0;i<dataBlocks.size();i++) {
            if (blockBytes.get(i).isEmpty()) continue;
            if (blocksToRemove.contains(dataBlocks.get(i))) continue;
            for(int j = 0;j<dataBlocks.size();j++) {
                if (i==j) continue;
                if (blockBytes.get(j).isEmpty()) continue;
                if (blocksToRemove.contains(dataBlocks.get(j))) continue;
                int startPosition = blockContained(blockBytes.get(i), 
                                                   blockBytes.get(j));
                if (startPosition >= 0) {
                    blocksToRemove.add(dataBlocks.get(i));
                    
                    config.info("DataOptimizer: data block containment detected ("+blockBytes.get(i).size()+" could be bytes saved):");
                    config.info("    block '"+dataBlocks.get(i).label.name+"' starting at " + dataBlocks.get(i).startStatement.fileNameLineString() + " (with size "+blockBytes.get(i).size()+")");
                    config.info("    contained in data block '"+dataBlocks.get(j).label.name+"' starting at " + dataBlocks.get(j).startStatement.fileNameLineString() + ", at offset " + i);
                    config.info("    (Note: MDL cannot know if this optimization is feasible, please make sure it does not break the code before applying it)");
                    savings.addOptimizerSpecific(DATA_OPTIMIZER_POTENTIAL_BYTES_CODE, blockBytes.get(i).size());
                    savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                    break;
                }
            }
        }
        
        // Remove the block to prevent further optimizations on it:
        for(CodeBlock b:blocksToRemove) {
            int idx = dataBlocks.indexOf(b);
            dataBlocks.remove(idx);
            blockBytes.remove(idx);
        }
    }
    
    
    // Checks if "b1" is contained in "b2":
    public int blockContained(List<Integer> b1, List<Integer> b2)
    {
        int l1 = b1.size();
        int l2 = b2.size();
        for(int i = 0;i <= l2 - l1;i++) {
            boolean found = true;
            for(int j = 0;j < l1;j++) {
                if (!b1.get(j).equals(b2.get(i+j))) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
}

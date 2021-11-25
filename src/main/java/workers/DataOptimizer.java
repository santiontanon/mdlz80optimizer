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
    int minSavingsToConsider = 4;
    
    public DataOptimizer(MDLConfig a_config)
    {
        config = a_config;
        binaryGenerator = new BinaryGenerator(config);
    }

    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-do```: Runs the data optimizer (only provides potential ideas for space saving).\n" +
               "- ```-do-minsavings <min>```: sets the minimum number of potential bytes that should be saved in order for the data optimizer to generate an optimization suggestion (default value is 4).";
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
        } else if (flags.get(0).equals("-do-minsavings") && flags.size()>=2) {
            flags.remove(0);
            Integer value = Integer.parseInt(flags.remove(0));
            minSavingsToConsider = value;
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
        findBlocksContainedInOthers(dataBlocks, blockBytes, savings, false, code);
        // - Check if the end of one datablock is a prefix of another
        findBlocksPrefixingOthers(dataBlocks, blockBytes, savings, false, code);
        // - Check if the reverse of one is contained in another
        findBlocksContainedInOthers(dataBlocks, blockBytes, savings, true, code);
        // - Check if the reverse of the beginning of one is a prefix of another
        findBlocksPrefixingOthers(dataBlocks, blockBytes, savings, true, code);
        
        // Step 4: split the blocks into smaller blocks:
        List<CodeBlock> finegrainedDataBlocks = splitIntoFineGrainedblocks(dataBlocks, code);
        List<List<Integer>> finegrainedBlockBytes = blocksToBytes(finegrainedDataBlocks, code);
        
        // Step 5: Look for optimization oportunities (same as above, but with
        // fine-grained blocks):
        findBlocksContainedInOthers(finegrainedDataBlocks, finegrainedBlockBytes, savings, false, code);
        findBlocksPrefixingOthers(finegrainedDataBlocks, finegrainedBlockBytes, savings, false, code);
        findBlocksContainedInOthers(finegrainedDataBlocks, finegrainedBlockBytes, savings, true, code);
        findBlocksPrefixingOthers(finegrainedDataBlocks, finegrainedBlockBytes, savings, true, code);
        
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
                                            OptimizationResult savings,
                                            boolean reverseOrder,
                                            CodeBase code)
    {
        List<CodeBlock> blocksToRemove = new ArrayList<>();
        for(int i = 0;i<dataBlocks.size();i++) {
            if (blockBytes.get(i).isEmpty()) continue;
            if (blockBytes.get(i).size() < minSavingsToConsider) continue;
            if (dataBlocks.get(i).containsOptimizationProtectedStatements(code)) continue;
            if (blocksToRemove.contains(dataBlocks.get(i))) continue;
            for(int j = 0;j<dataBlocks.size();j++) {
                if (i==j) continue;
                if (blockBytes.get(j).isEmpty()) continue;
                if (blocksToRemove.contains(dataBlocks.get(j))) continue;
                if (dataBlocks.get(j).containsOptimizationProtectedStatements(code)) continue;
                
                if (reverseOrder) {
                    List<Integer> reversed = new ArrayList<>();
                    for(int v:blockBytes.get(i)) {
                        reversed.add(0, v);
                    }
                    int startPosition = blockContained(reversed, 
                                                       blockBytes.get(j));
                    if (startPosition >= 0) {
                        blocksToRemove.add(dataBlocks.get(i));

                        config.info("DataOptimizer: reverse data block containment detected ("+blockBytes.get(i).size()+" bytes could be saved):");
                        config.info("    A version of block '"+dataBlocks.get(i).label.name+"' starting at " + dataBlocks.get(i).startStatement.fileNameLineString() + " (with size "+blockBytes.get(i).size()+")");
                        config.info("    but in reverse order is contained in data block '"+dataBlocks.get(j).label.name+"' starting at " + dataBlocks.get(j).startStatement.fileNameLineString() + ", at offset " + startPosition);
                        config.info("    Block '"+dataBlocks.get(i).label.name+"' could be redundant with some code adjustment.");
                        config.info("    (Note: MDL cannot know if this optimization is feasible, please make sure it does not break the code before applying it)");
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_POTENTIAL_BYTES_CODE, blockBytes.get(i).size());
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                        break;
                    }                    
                } else {                
                    int startPosition = blockContained(blockBytes.get(i), 
                                                       blockBytes.get(j));
                    if (startPosition >= 0) {
                        blocksToRemove.add(dataBlocks.get(i));

                        config.info("DataOptimizer: data block containment detected ("+blockBytes.get(i).size()+" bytes could be saved):");
                        config.info("    Block '"+dataBlocks.get(i).label.name+"' starting at " + dataBlocks.get(i).startStatement.fileNameLineString() + " (with size "+blockBytes.get(i).size()+")");
                        config.info("    contained in data block '"+dataBlocks.get(j).label.name+"' starting at " + dataBlocks.get(j).startStatement.fileNameLineString() + ", at offset " + startPosition);
                        config.info("    Block '"+dataBlocks.get(i).label.name+"' could be redundant.");
                        config.info("    (Note: MDL cannot know if this optimization is feasible, please make sure it does not break the code before applying it)");
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_POTENTIAL_BYTES_CODE, blockBytes.get(i).size());
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                        break;
                    }
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
            for(int j = 0; j < l1; j++) {
                if (!b1.get(j).equals(b2.get(i + j))) {
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
    
    
    public void findBlocksPrefixingOthers(List<CodeBlock> dataBlocks, 
                                            List<List<Integer>> blockBytes,
                                            OptimizationResult savings,
                                            boolean reverseOrder,
                                            CodeBase code)
    {
        List<CodeBlock> blocksToRemove = new ArrayList<>();
        for(int i = 0;i<dataBlocks.size();i++) {
            if (blockBytes.get(i).isEmpty()) continue;
            if (blocksToRemove.contains(dataBlocks.get(i))) continue;
            if (dataBlocks.get(i).containsOptimizationProtectedStatements(code)) continue;
            for(int j = 0;j<dataBlocks.size();j++) {
                if (i==j) continue;
                if (blockBytes.get(j).isEmpty()) continue;
                if (blocksToRemove.contains(dataBlocks.get(j))) continue;
                if (dataBlocks.get(j).containsOptimizationProtectedStatements(code)) continue;
                
                if (reverseOrder) {
                    int bytesSaved = blockEndsInPrefixReversed(blockBytes.get(i), blockBytes.get(j));
                    if (bytesSaved > minSavingsToConsider) {
                        blocksToRemove.add(dataBlocks.get(i));

                        config.info("DataOptimizer: reverse data block endings detected ("+bytesSaved+" bytes could be saved):");
                        config.info("    The last "+bytesSaved+" bytes of block '"+dataBlocks.get(j).label.name+"' starting at " + dataBlocks.get(j).startStatement.fileNameLineString());
                        config.info("    but in reverse order are identical to the last "+bytesSaved+" bytes of block '"+dataBlocks.get(i).label.name+"' starting at " + dataBlocks.get(i).startStatement.fileNameLineString());
                        config.info("    They could be combined with some code adjustment.");
                        config.info("    (Note: MDL cannot know if this optimization is feasible, please make sure it does not break the code before applying it)");
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_POTENTIAL_BYTES_CODE, bytesSaved);
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                        break;
                    } else {
                        bytesSaved = blockStartsInPrefixReversed(blockBytes.get(i), blockBytes.get(j));
                        if (bytesSaved > minSavingsToConsider) {
                            blocksToRemove.add(dataBlocks.get(i));

                            config.info("DataOptimizer: reverse data block prefix detected ("+bytesSaved+" bytes could be saved):");
                            config.info("    The first "+bytesSaved+" bytes of block '"+dataBlocks.get(j).label.name+"' starting at " + dataBlocks.get(j).startStatement.fileNameLineString());
                            config.info("    but in reverse order are identical to the first "+bytesSaved+" bytes of block '"+dataBlocks.get(i).label.name+"' starting at " + dataBlocks.get(i).startStatement.fileNameLineString());
                            config.info("    They could be combined with some code adjustment.");
                            config.info("    (Note: MDL cannot know if this optimization is feasible, please make sure it does not break the code before applying it)");
                            savings.addOptimizerSpecific(DATA_OPTIMIZER_POTENTIAL_BYTES_CODE, bytesSaved);
                            savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                            break;
                        }
                    }
                } else {
                    int startPosition = blockEndsInPrefix(blockBytes.get(i), blockBytes.get(j));
                    if (startPosition >= 0) {
                        int bytesSaved = blockBytes.get(j).size() - startPosition;
                        if (bytesSaved < minSavingsToConsider) continue;
                        blocksToRemove.add(dataBlocks.get(i));

                        config.info("DataOptimizer: data block prefix detected ("+bytesSaved+" bytes could be saved):");
                        config.info("    The last "+bytesSaved+" bytes of block '"+dataBlocks.get(j).label.name+"' starting at " + dataBlocks.get(j).startStatement.fileNameLineString());
                        config.info("    are identical to the first "+bytesSaved+" bytes of block '"+dataBlocks.get(i).label.name+"' starting at " + dataBlocks.get(i).startStatement.fileNameLineString());
                        config.info("    They could be combined.");
                        config.info("    (Note: MDL cannot know if this optimization is feasible, please make sure it does not break the code before applying it)");
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_POTENTIAL_BYTES_CODE, bytesSaved);
                        savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                        break;
                    }
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
    
    
    // Checks if the end of "b2" is a prefix for "b1":
    public int blockEndsInPrefix(List<Integer> b1, List<Integer> b2)
    {
        int l1 = b1.size();
        int l2 = b2.size();
        for(int i = Math.max(0, l2 - l1); i < l2; i++) {
            boolean found = true;
            for(int j = 0; i+j < l2; j++) {
                if (!b1.get(j).equals(b2.get(i + j))) {
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
    
    // Checks if the end of "b1" is a reversed version of the end of "b2":
    // Example: b1: 0,1,[2,3,4,5,6,7], b2: 10,9,8,[7,6,5,4,3,2]
    public int blockEndsInPrefixReversed(List<Integer> b1, List<Integer> b2)
    {
        int l1 = b1.size();
        int l2 = b2.size();
        for(int l = 1; l < Math.min(l1, l2); l++) {
            boolean found = true;
            for(int i = 0; i < l; i++) {
                if (!b1.get(l1 - (i+1)).equals(b2.get(l2 - (l - i)))) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return l;
            }
        }
        return -1;
    }        

    // Checks if the end of "b1" is a reversed version of the end of "b2":
    // Example: b1: [0,1,2,3,4],5,6,7, b2: [4,3,2,1,0],-1,-2,-3
    public int blockStartsInPrefixReversed(List<Integer> b1, List<Integer> b2)
    {
        int l1 = b1.size();
        int l2 = b2.size();
        for(int l = 1; l < Math.min(l1, l2); l++) {
            boolean found = true;
            for(int i = 0; i < l; i++) {
                if (!b1.get(i).equals(b2.get((l - i) - 1))) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return l;
            }
        }
        return -1;
    }        

}

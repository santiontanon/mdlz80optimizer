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
        return "- ```-do```: Runs the data optimizer.\n";
    }

    
    @Override
    public String simpleDocString() {
        return "- ```-do```: Runs the data optimizer.\n";
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
        List<List<Integer>> blockBytes = new ArrayList<>();
        for(CodeBlock db:dataBlocks) {
            List<Integer> bytes = assembleToBytes(db, code);
            if (bytes == null) return false;
            config.debug("DataBlock " + db.startStatement.label.name + ": " +  bytes.size());
            blockBytes.add(bytes);
        }
        
        // Step 3: Look for optimization oportunities:
        // - Check if a block is contained in another
        findBlocksContainedInOthers(dataBlocks, blockBytes, savings);

        // - Check if the end of one datablock is a prefix of another
        // - Check if the reverse of one is contained in another
        // - Check if the reverse of the beginning of one is a prefix of another
        // ...
        
        config.optimizerStats.addSavings(savings);
        return true;
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
        for(int i = 0;i<dataBlocks.size();i++) {
            for(int j = 0;j<dataBlocks.size();j++) {
                if (i==j) continue;
                if (blockBytes.get(i).size() == blockBytes.get(j).size() && i>j) continue;
                int startPosition = blockContained(blockBytes.get(i), 
                                                   blockBytes.get(j));
                if (startPosition >= 0) {
                    config.info("DataOptimizer: data block containment detected ("+blockBytes.get(i).size()+" bytes saved):");
                    config.info("    block starting at " + dataBlocks.get(i).startStatement.fileNameLineString() + " (with size "+blockBytes.get(i).size()+")");
                    config.info("    contained in data block starting at " + dataBlocks.get(j).startStatement.fileNameLineString() + ", at offset " + i);
                    config.info("    (Note: MDL does not know how to automatically do this optimization yet)");
                    savings.addSavings(blockBytes.get(i).size(), new int[]{0});
                    savings.addOptimizerSpecific(DATA_OPTIMIZER_OPTIMIZATIONS_CODE, 1);
                    break;
                }
            }
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

/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class AnnotatedSourceCodeGenerator implements MDLWorker {

    MDLConfig config = null;

    String outputFileName = null;


    public AnnotatedSourceCodeGenerator(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        return "  -asm+ <output file>: (task) generates a single text file containing the original assembler code " +
               "(with macros expanded), that includes size and time annotations at the beginning of each file " +
               "to help with manual optimizations beyond what MDL already provides.\n";
    }


    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-asm+") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        }
        return false;
    }


    @Override
    public boolean work(CodeBase code) {

        if (outputFileName != null) {
            config.debug("Executing "+this.getClass().getSimpleName()+" worker...");
            
            if (config.evaluateAllExpressions) code.evaluateAllExpressions();
            
            try (FileWriter fw = new FileWriter(outputFileName)) {
                for(SourceFile sf:code.getSourceFiles()) {
                    fw.write("; ------------------------------------------------\n");
                    fw.write("; ---- " + sf.fileName + " --------------------------------\n");
                    fw.write("; ------------------------------------------------\n\n");
                    fw.write("; Address  Size  Time\n");
                    fw.write("; -------------------\n");
                    fw.write(sourceFileString(sf, code));
                    fw.write("\n");
                }
                fw.flush();
            } catch (Exception e) {
                config.error("Cannot write to file " + outputFileName + ": " + e);
                for(Object st:e.getStackTrace()) {
                    config.error("    " + st);
                }
                return false;
            }
        }
        return true;
    }


    public String sourceFileString(SourceFile sf, CodeBase code)
    {
        StringBuilder sb = new StringBuilder();
        sourceFileString(sf, sb, code);
        return sb.toString();
    }


    public void sourceFileString(SourceFile sf, StringBuilder sb, CodeBase code)
    {
        for (CodeStatement ss:sf.getStatements()) {
            sb.append("  ");
            Integer address = ss.getAddress(code);
            if (address == null) {
                sb.append("????");
            } else {
                sb.append(Tokenizer.toHexWord(address, config.hexStyle));
            }
            sb.append("  ");
            Integer size = ss.sizeInBytes(code, true, true, true);
            if (size == null) {
                config.error("Cannot evaluate the size of statement: " + ss);
                return;
            }
            String sizeString = "" + (size > 0 ? size:"");
            while(sizeString.length() < 4) sizeString = " " + sizeString;
            sb.append(sizeString);
            sb.append("  ");
            String timeString = "" + ss.timeString();
            while(timeString.length() < 5) timeString = " " + timeString;
            sb.append(timeString);
            sb.append("  ");

            sb.append(ss.toString());
            sb.append("\n");
        }
    }
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        AnnotatedSourceCodeGenerator w = new AnnotatedSourceCodeGenerator(config);
        w.outputFileName = outputFileName;
        
        // reset state:
        outputFileName = null;
        
        return w;
    }     
}

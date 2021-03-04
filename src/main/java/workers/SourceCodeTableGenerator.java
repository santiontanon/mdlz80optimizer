/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;

/**
 *
 * @author santi
 */
public class SourceCodeTableGenerator implements MDLWorker {

    MDLConfig config = null;

    String outputFileName = null;

    public SourceCodeTableGenerator(MDLConfig a_config)
    {
        config = a_config;
    }

    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-sft <output file>```: (task) generates a tsv file with some statistics about the source files.\n";
    }

    
    @Override
    public String simpleDocString() {
        return "";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-sft") && flags.size()>=2) {
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

            try (FileWriter fw = new FileWriter(outputFileName)) {
                fw.write(sourceFileTableString(code));
                fw.flush();
            } catch (Exception e) {
                config.error("Cannot write to file " + outputFileName + ": " + e);
                return false;
            }
        }
        return true;
    }


    public String sourceFileTableString(CodeBase code)
    {
        HashMap<String, String> fileInfo = new HashMap<>();
        List<String> sortedSources = new ArrayList<>();
        for(SourceFile f: code.getSourceFiles()) {
            String data = f.fileName + "\t" + f.sizeInBytes(code, false, false, false) +
                                       "\t" + f.sizeInBytes(code, true, true, false);
            fileInfo.put(f.fileName, data);

            if (config.includeBinariesInAnalysis) {
                for(CodeStatement s : f.getStatements()) {
                    if (s.type == CodeStatement.STATEMENT_INCBIN) {
                        String data2 = s.incbin + "\t" + s.incbinSize +
                                                 "\t" + s.incbinSize;
                        fileInfo.put(s.incbin.getName(), data2);
                    }
                }
            }
        }

        sortedSources.addAll(fileInfo.keySet());
        Collections.sort(sortedSources);
        StringBuilder sb = new StringBuilder();
        sb.append("source file\tself size\ttotal size\n");
        for(String name:sortedSources) {
            sb.append(fileInfo.get(name));
            sb.append("\n");
        }

        return sb.toString();
    }
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        SourceCodeTableGenerator w = new SourceCodeTableGenerator(config);
        w.outputFileName = outputFileName;
        
        // reset state:
        outputFileName = null;
        
        return w;
    }    
}

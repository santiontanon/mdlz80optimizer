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
    boolean measureIndividualFunctions = false;

    public SourceCodeTableGenerator(MDLConfig a_config)
    {
        config = a_config;
    }

    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-sft <output file>```: generates a tsv file with some " +
               "statistics about the source files (bytes used, accumulated " +
               "time of all the CPU ops in the file, etc.).\n" +
               "- ```-sft-functions```: MDL will try to identify individual " +
               "functions in the code, and add per-function statistics to the " +
               "file generated with the ```-sft``` flag.\n";
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
        } else if (flags.get(0).equals("-sft-functions")) {
            measureIndividualFunctions = true;
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
                                       "\t" + f.sizeInBytes(code, true, true, false) + 
                                       "\t" + f.accumTimingString();
            fileInfo.put(f.fileName, data);

            if (config.includeBinariesInAnalysis) {
                for(CodeStatement s : f.getStatements()) {
                    if (s.type == CodeStatement.STATEMENT_INCBIN) {
                        String data2 = s.incbin + "\t" + s.incbinSize +
                                                 "\t" + s.incbinSize + "\t-";
                        fileInfo.put(s.incbin.getName(), data2);
                    }
                }
            }
            
            if (measureIndividualFunctions) {
                // TO DO: 
                // ...
            }
        }

        sortedSources.addAll(fileInfo.keySet());
        Collections.sort(sortedSources);
        StringBuilder sb = new StringBuilder();
        sb.append("source file\tself size\ttotal size\taccum ");
        sb.append(config.timeUnit);
        sb.append("s\n");
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
}

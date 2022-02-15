/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
import code.SourceConstant;

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
            flags.remove(0);
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
            String data = f.fileName +
                    "\t" + f.sizeInBytes(code, false, false, false) +
                    "\t" + f.sizeInBytes(code, true, true, false) + 
                    "\t" + f.accumTimingString();
            fileInfo.put(f.fileName, data);

            if (config.includeBinariesInAnalysis) {
                for(CodeStatement s : f.getStatements()) {
                    if (s.type == CodeStatement.STATEMENT_INCBIN) {
                        String data2 = s.incbin + 
                                "\t" + s.incbinSize +
                                "\t" + s.incbinSize + "\t-";
                        fileInfo.put(s.incbin.getName(), data2);
                    }
                }
            }
            
            if (measureIndividualFunctions) {
                List<Pair<CodeStatement, CodeStatement>> functions = autodetectFunctions(f, code);
                for(Pair<CodeStatement, CodeStatement> function:functions) {
                    SourceConstant functionName = null;
                    int time[] = {0,0};
                    int size = 0;
                    for(int i = f.getStatements().indexOf(function.getLeft());
                            i <= f.getStatements().indexOf(function.getRight()); 
                            i++) {
                        CodeStatement s = f.getStatements().get(i);
                        if (functionName == null) {
                            if (s.label == null) {
                                config.error("First statement of an autodetected function is not a label!");
                                break;
                            }
                            functionName = s.label;
                        }
                        
                        if (s.op != null) {
                            size += s.op.spec.sizeInBytes;
                            time[0] += s.op.spec.times[0];
                            if (s.op.spec.times.length >= 2) {
                                time[1] += s.op.spec.times[1];
                            } else {
                                time[1] += s.op.spec.times[0];
                            }
                        }
                    }
                    String timeString = null;
                    if (time[0] == time[1]) {
                        timeString = "" + time[0];
                    } else {
                        timeString = time[0] + "/" + time[1];
                    }
                    String data2 = f.fileName + "." + functionName.name +
                            "\t" + size +
                            "\t" + 
                            "\t" + timeString;
                    data += "\n" + data2;
                    fileInfo.put(f.fileName, data);
                }
            }
        }

        sortedSources.addAll(fileInfo.keySet());
        Collections.sort(sortedSources);
        StringBuilder sb = new StringBuilder();
        sb.append("source file");
        if (measureIndividualFunctions) {
            sb.append(" (.function name)");
        }
        sb.append("\tself size\ttotal size\taccum ");
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
    
    
    public List<Pair<CodeStatement, CodeStatement>> autodetectFunctions(SourceFile f, CodeBase code)    
    {
        List<Pair<CodeStatement, CodeStatement>> functions = new ArrayList<>();
        
        // v1: find the first label, and run until finding a "ret", if there is
        // any non-op statement, cancel the whole function and restart:
        CodeStatement functionStart = null;
        for(CodeStatement s:f.getStatements()) {
            if (s.type == CodeStatement.STATEMENT_NONE ||
                s.type == CodeStatement.STATEMENT_CONSTANT || 
                s.type == CodeStatement.STATEMENT_CPUOP) {
                if (functionStart == null) {
                    if (s.label != null) {
                        functionStart = s;
                    }
                } else {
                    boolean functionEnd = false;
                    if (s.op != null && s.op.isRet() && !s.op.isConditional()) {
                        functionEnd = true;
                    }
                    if (s.op != null && s.op.isJump() && !s.op.isConditional()) {
                        SourceConstant l = s.op.getTargetJumpLabel(code);
                        if (l != null && l.definingStatement.source != f) {
                            functionEnd = true;
                        }
                    }
                    if (functionEnd) {
                        // function end:
                        functions.add(Pair.of(functionStart, s));
                        functionStart = null;
                    }
                }
            } else {
                // found a non-code part, cancel the current function:
                functionStart = null;
            }
        }
        
        return functions;
    }
}

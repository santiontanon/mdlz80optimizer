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
    
    
    public Pair<CodeStatement, CodeStatement> functionFromStartAndEnd(
        CodeStatement start, CodeStatement end) {
        // See if there are other labels just before the start:
        SourceFile f = start.source;
        int idx = f.getStatements().indexOf(start) - 1;
        while(idx >= 0) {
            CodeStatement s = f.getStatements().get(idx);
            if (s.type != CodeStatement.STATEMENT_NONE) break;
            if (s.op != null) break;            
            if (s.label != null) {
                start = s;
            }
            idx --;
        }
        return Pair.of(start, end);
    }


    public List<Pair<CodeStatement, CodeStatement>> autodetectFunctions(SourceFile f, CodeBase code)    
    {
        List<Pair<CodeStatement, CodeStatement>> functions = new ArrayList<>();
        
        // v1: find the first label, and run until finding a "ret", if there is
        // any non-op statement, cancel the whole function and restart:
        CodeStatement functionStart = null;
        CodeStatement lastOp = null;
        for(CodeStatement s:f.getStatements()) {
            if (s.type == CodeStatement.STATEMENT_NONE ||
                s.type == CodeStatement.STATEMENT_CONSTANT || 
                s.type == CodeStatement.STATEMENT_CPUOP) {
                boolean functionEnd = false;
                if (s.label != null) {
                    if (functionStart == null || lastOp == null) {                
                        functionStart = s;
                    } else {
                        // We found another label, see if a new function is
                        // startig here:
                        if (lastOp.op.isJump() && !lastOp.op.isConditional()) {
                            // potential for function end. But if we reached
                            // here, it means that none of the conditions below
                            // are satisfied. See if we can find other clues:
                            if (config.dialectParser != null && 
                                config.dialectParser.hasFunctionStartMark(s)) {
                                // function end!
                                functions.add(functionFromStartAndEnd(functionStart, lastOp));
                                functionStart = s;
                                lastOp = null;                                
                            }
                        }
                    }
                }
                if (s.op != null) lastOp = s;
                if (!functionEnd && functionStart != null) {
                    if (s.op != null && s.op.isRet() && !s.op.isConditional()) {
                        functionEnd = true;
                    }
                    if (s.op != null && s.op.isJump() && !s.op.isConditional()) {
                        SourceConstant l = s.op.getTargetJumpLabel(code);
                        if (l != null && l.definingStatement.source != f) {
                            functionEnd = true;
                        }
                    }
                }
                if (functionEnd) {
                    // function end:
                    functions.add(functionFromStartAndEnd(functionStart, s));
                    functionStart = null;
                    lastOp = null;
                }
            } else {
                // found a non-code part, cancel the current function:
                functionStart = null;
            }
        }
        
        if (functionStart != null && lastOp != null) {
            // We found the end of a file, and we have some code leftover,
            // see if there was a function terminating in a non-standard way:
            if (lastOp.op.isJump() && !lastOp.op.isConditional()) {
                functions.add(functionFromStartAndEnd(functionStart, lastOp));
            }
        }        
        
        return functions;
    }
}

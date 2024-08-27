/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceFile;
import code.CodeStatement;
import code.OutputBinary;
import java.nio.file.Paths;
import java.util.Arrays;
import util.Pair;
import util.TextUtils;

/**
 *
 * @author santi
 */
public class SourceCodeGenerator implements MDLWorker {
    
    public static String AUTO_FILENAME = "auto";

    MDLConfig config = null;

    String outputFileName = null;
    boolean expandIncbin = false;
    int incbinBytesPerLine = 16;
    
    public boolean mimicTargetDialect = false;


    public SourceCodeGenerator(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-asm <output file>```: saves the resulting assembler code in a single asm file (if no optimizations are performed, then this will just output the same code read as input (but with all macros and include statements expanded). Use ```"+AUTO_FILENAME+"``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to auto generate an output name.\n" +
               "- ```-asm-dialect <output file>```: same as '-asm', but tries to mimic the syntax of the defined dialect in the output (experimental feature, not fully implemented!).  Use ```"+AUTO_FILENAME+"``` as the output file name to respect the filenames specified in the sourcefiles of some dialects, or to auto generate an output name.\n" +
               "- ```-asm-expand-incbin```: replaces all incbin commands with their actual data in the output assembler file, effectively, making the output assembler file self-contained.\n";
    }


    @Override
    public String simpleDocString() {
        return "- ```-asm <output file>```: saves the resulting assembler code in a single asm file.\n";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-asm") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            mimicTargetDialect = false;
            return true;
        }
        if (flags.get(0).equals("-asm-dialect") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            mimicTargetDialect = true;
            return true;
        }
        if (flags.get(0).equals("-asm-expand-incbin")) {
            flags.remove(0);
            expandIncbin = true;
            return true;
        }
        return false;
    }
    

    @Override
    public boolean work(CodeBase code) {

        if (outputFileName != null) {
            config.debug("Executing "+this.getClass().getSimpleName()+" worker...");

            if (config.evaluateAllExpressions) code.evaluateAllExpressions();
            
            if (mimicTargetDialect && config.dialectParser != null && config.dialectParser.supportsMultipleOutputs()) {
                // Write all the outputs into a single assembler file:
                String finalOutputFileName = outputFileName;
                if (finalOutputFileName.equals(AUTO_FILENAME)) {
                    // autogenerate filenames:
                    if (outputFileName == null) {
                        Pair<String, String> tmp = TextUtils.splitFileNameExtension(code.outputs.get(0).main.fileName);
                        finalOutputFileName = tmp.getLeft() + ".mdl" + tmp.getRight();
                    }
                }
                
                try (FileWriter fw = new FileWriter(finalOutputFileName)) {
                    for(OutputBinary output:code.outputs) {                
                        fw.write(sourceFileString(output.main, output, code));
                        fw.flush();
                    }
                } catch (Exception e) {
                    config.error("Cannot write to file " + finalOutputFileName + ": " + e);
                    config.error(Arrays.toString(e.getStackTrace()));
                    return false;
                }
                
            } else {            
                for(OutputBinary output:code.outputs) {
                    String finalOutputFileName = outputFileName;
                    if (finalOutputFileName.equals(AUTO_FILENAME)) {
                        // autogenerate filenames:
                        if (outputFileName == null) {
                            Pair<String, String> tmp = TextUtils.splitFileNameExtension(output.main.fileName);
                            finalOutputFileName = tmp.getLeft() + ".mdl" + tmp.getRight();
                        }
                    }
                    int idx = code.outputs.indexOf(output);
                    if (idx > 0) {
                        Pair<String, String> tmp = TextUtils.splitFileNameExtension(finalOutputFileName);
                        finalOutputFileName = tmp.getLeft() + "-output" + (idx+1) + tmp.getRight();
                    }

                    try (FileWriter fw = new FileWriter(finalOutputFileName)) {
                        fw.write(sourceFileString(output.main, output, code));
                        fw.flush();
                    } catch (Exception e) {
                        config.error("Cannot write to file " + finalOutputFileName + ": " + e);
                        config.error(Arrays.toString(e.getStackTrace()));
                        return false;
                    }
                }
            }
        }
        return true;
    }

    
    public String outputFileString(OutputBinary output, CodeBase code)
    {
        return sourceFileString(output.main, output, code);
    }
    

    public String sourceFileString(SourceFile sf, OutputBinary output, CodeBase code)
    {
        StringBuilder sb = new StringBuilder();
        sourceFileString(sf, output, code, sb);
        return sb.toString();
    }

    
    public void sourceFileString(SourceFile sf, OutputBinary output, CodeBase code, StringBuilder sb)
    {
        sourceFileString(sf.getStatements(), output, code, sb);
    }   
    

    public void sourceFileString(List<CodeStatement> statements, OutputBinary output, CodeBase code, StringBuilder sb)
    {
        for (CodeStatement ss:statements) {
            if (ss.type == CodeStatement.STATEMENT_INCLUDE) {
                if (ss.label != null) {
                    // make sure we don't lose the label:
                    sb.append(ss.label.name);
                    if (config.output_safetyEquDollar) {
                        if (config.output_equsWithoutColon) {
                            sb.append(" equ $\n");
                        } else {
                            sb.append(": equ $\n");
                        }
                    } else {
                        sb.append(":\n");                        
                    }
                }
                sourceFileString(ss.include, output, code, sb);
            } else if (ss.type == CodeStatement.STATEMENT_INCBIN && expandIncbin) {
                int skip = 0;
                int size = 0;
                if (ss.incbinSkip != null) skip = ss.incbinSkip.evaluateToInteger(ss, code, false);
                if (ss.incbinSize != null) size = ss.incbinSize.evaluateToInteger(ss, code, false);
                try (InputStream is = new FileInputStream(ss.incbin)) {
                    int count = 0;
                    while(is.available() != 0) {
                        int data = is.read();
                        if (skip > 0) {
                            skip --;
                            continue;
                        }
                        if (count > 0) {
                            sb.append(", ");
                        } else {
                            sb.append("    db ");
                        }
                        sb.append(data);
                        count++;
                        if (count >= incbinBytesPerLine) {
                            sb.append("\n");
                            count = 0;
                        }
                        size --;
                        if (size <= 0) break;
                    }
                    if (count > 0) sb.append("\n");
                } catch(Exception e) {
                    config.error("Cannot expand incbin: " + ss.incbin);
                }
            } else {
                if (mimicTargetDialect && config.dialectParser != null) {
                    String ssString = config.dialectParser.statementToString(ss, code, Paths.get(output.main.getPath()), null);
                    if (ssString != null) {
                        sb.append(ssString);
                        sb.append("\n");
                    }
                } else {
                    sb.append(ss.toStringUsingRootPath(Paths.get(output.main.getPath()), false, false, code, null));
                    sb.append("\n");
                }
            }
        }
    }
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }   
}

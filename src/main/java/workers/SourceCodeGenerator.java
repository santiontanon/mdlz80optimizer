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
import code.SourceStatement;

/**
 *
 * @author santi
 */
public class SourceCodeGenerator implements MDLWorker {

    MDLConfig config = null;

    String outputFileName = null;
    boolean expandIncbin = false;
    int incbinBytesPerLine = 16;


    public SourceCodeGenerator(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        return "  -asm <output file>: saves the resulting assembler code in a single asm file (if no " +
               "optimizations are performed, then this will just output the same code read as input " +
               "(but with all macros and include statements expanded).\n" +
               "  -asm-expand-inbcin: replaces all incbin commands with their actual data in the output " +
               "assembler file, effectively, making the output assembler file self-contained.\n";
    }


    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-asm") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        }
        if (flags.get(0).equals("-asm-expand-inbcin")) {
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

            try (FileWriter fw = new FileWriter(outputFileName)) {
                fw.write(sourceFileString(code.getMain(), code));
                fw.flush();
            } catch (Exception e) {
                config.error("Cannot write to file " + outputFileName + ": " + e);
                return false;
            }
        }
        return true;
    }


    public String sourceFileString(SourceFile sf, CodeBase code)
    {
        StringBuilder sb = new StringBuilder();
        sourceFileString(sf, code, sb);
        return sb.toString();
    }


    public void sourceFileString(SourceFile sf, CodeBase code, StringBuilder sb)
    {
        for (SourceStatement ss:sf.getStatements()) {
            if (ss.type == SourceStatement.STATEMENT_INCLUDE) {
                sourceFileString(ss.include, code, sb);
            } else if (ss.type == SourceStatement.STATEMENT_INCBIN && expandIncbin) {
                int skip = 0;
                int size = 0;
                if (ss.incbinSkip != null) skip = ss.incbinSkip.evaluate(ss, code, false);
                if (ss.incbinSize != null) size = ss.incbinSize.evaluate(ss, code, false);
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
                sb.append(ss.toString());
                sb.append("\n");
            }
        }
    }
}

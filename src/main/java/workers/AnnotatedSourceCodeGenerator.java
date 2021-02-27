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
import code.OutputBinary;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author santi
 */
public class AnnotatedSourceCodeGenerator implements MDLWorker {

    MDLConfig config = null;

    boolean generateHTML = false;
    String outputFileName = null;


    public AnnotatedSourceCodeGenerator(MDLConfig a_config)
    {
        config = a_config;
    }


    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-asm+ <output file>```: (task) generates a single text file containing the original assembler code (with macros expanded), that includes size and time annotations at the beginning of each file to help with manual optimizations beyond what MDL already provides.\n"
             + "- ```-asm+:html <output file>```: (task) acts like ```-asm+```, except that the output is in html (rendered as a table), allowing it to have some extra information.\n";
    }


    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-asm+") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-asm+:html") && flags.size()>=2) {
            flags.remove(0);
            generateHTML = true;
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
            
            // Calculate the position of each statement in the generated binary:
            HashMap<CodeStatement, Integer> positionInBinaryMap = new HashMap<>();
            List<Pair<CodeStatement, List<Integer>>> statementBytes = new ArrayList<>();
            BinaryGenerator gen = new BinaryGenerator(config);
            for(OutputBinary output: code.outputs) {
                if (!gen.generateStatementBytes(output.main, code, statementBytes)) return false;
            }
            int position = 0;
            for(Pair<CodeStatement, List<Integer>> pair:statementBytes) {
                positionInBinaryMap.put(pair.getLeft(), position);
                position += pair.getRight().size();
            }
            
            try (FileWriter fw = new FileWriter(outputFileName)) {
                if (generateHTML) {
                    // write HTML header:
                    fw.write("<head>\n");            
                    fw.write("<title>MDL: annotated source code</title>\n");
                    fw.write("<style>\n");
                    fw.write("  table.sourcefile {\n");
                    fw.write("    border: 1px solid black;\n");
                    fw.write("    background-color: #eeeeee;\n");
                    fw.write("    }\n");
                    fw.write("  table.sourcefile tr td {\n");
                    fw.write("    border-right: 1px solid black;\n");
                    fw.write("    padding-left: 8px;\n");
                    fw.write("    padding-right: 8px;\n");
                    fw.write("    }\n");
                    fw.write("  table.sourcefile tr td:last-of-type {\n");
                    fw.write("    border: none;\n");
                    fw.write("    background-color: #ffffff;\n");
                    fw.write("    }\n");
                    fw.write("</style>\n");
                    fw.write("</head>\n");
                    fw.write("<body>\n");
                }
                for(SourceFile sf:code.getSourceFiles()) {
                    
                    if (generateHTML) {
                        /*
                        fw.write("<table class=\"topblock\">\n");
                        fw.write("<tr><td><button type=\"button\" class=\"collapsible\">"+topBlock.ID+"</button>\n");
                        fw.write("<div class=\"content\">\n");
                        */
                        fw.write("<hr><br><b> Source Code file: " + sf.fileName + "</b><br>\n");
                        fw.write("<table class=\"sourcefile\" cellspacing=\"0\">\n");
                        fw.write("<tr><td style=\"border-bottom: 1px solid black\"><b>Address</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Position in Binary</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Size</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Timing</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Assembled</b></td>");
                        fw.write("<td style=\"border-bottom: 1px solid black\"><b>Code</b></td></tr>\n");
                        fw.write(sourceFileHTMLString(sf, code, positionInBinaryMap));
                        fw.write("</table>\n");
                        fw.write("\n");
                    } else {
                        fw.write("; ------------------------------------------------\n");
                        fw.write("; ---- " + sf.fileName + " --------------------------------\n");
                        fw.write("; ------------------------------------------------\n\n");
                        fw.write("; Address  Size  Time\n");
                        fw.write("; -------------------\n");
                        fw.write(sourceFileString(sf, code));
                        fw.write("\n");
                    }
                }
                if (generateHTML) {
                    fw.write("</body>\n");
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
                sb.append(config.tokenizer.toHexWord(address, config.hexStyle));
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
    
    
    public String sourceFileHTMLString(SourceFile sf, CodeBase code, HashMap<CodeStatement, Integer> positionInBinaryMap)
    {
        StringBuilder sb = new StringBuilder();
        sourceFileHTMLString(sf, sb, code, positionInBinaryMap);
        return sb.toString();
    }

    
    public void sourceFileHTMLString(SourceFile sf, StringBuilder sb, CodeBase code, HashMap<CodeStatement, Integer> positionInBinaryMap)
    {
        for (CodeStatement ss:sf.getStatements()) {
            sb.append("<tr>");
            Integer address = ss.getAddress(code);
            if (address == null) {
                sb.append("<td>????</td>");
            } else {
                sb.append("<td>");
                sb.append(config.tokenizer.toHexWord(address, config.hexStyle));
                sb.append("</td>");
            }
            
            Integer position = positionInBinaryMap.get(ss);
            if (position == null) {
                sb.append("<td></td>");
            } else {
                sb.append("<td>");
                sb.append(config.tokenizer.toHexWord(position, config.hexStyle));
                sb.append("</td>");
            }
            
            Integer size = ss.sizeInBytes(code, true, true, true);
            if (size == null) {
                config.error("Cannot evaluate the size of statement: " + ss);
                return;
            }
            String sizeString = "" + (size > 0 ? size:"");
            sb.append("<td>");
            sb.append(sizeString);
            sb.append("</td>");
            String timeString = "" + ss.timeString();
            sb.append("<td>");
            sb.append(timeString);
            sb.append("</td>");
            if (ss.type == CodeStatement.STATEMENT_CPUOP) {
                List<Integer> data = ss.op.assembleToBytes(ss, code, config);
                if (data == null) {
                    config.error("Cannot convert " + ss.op + " to bytes in " + ss.sl);
                    sb.append("<td>ERROR</td>");
                } else {
                    sb.append("<td><pre>");
                    for(Integer v:data) {
                        sb.append(config.tokenizer.toHex(v, 2));
                        sb.append(" ");
                    }
                    sb.append("</pre></td>");
                }
            } else {
                sb.append("<td></td>");
            }            
            sb.append("<td><pre>");
            sb.append(ss.toString());
            sb.append("</pre></td>");
            sb.append("</tr>\n");
        }
    }    
    
    
    @Override
    public boolean triggered() {
        return outputFileName != null;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        AnnotatedSourceCodeGenerator w = new AnnotatedSourceCodeGenerator(config);
        w.generateHTML = generateHTML;
        w.outputFileName = outputFileName;
        
        // reset state:
        generateHTML = false;
        outputFileName = null;
        
        return w;
    }     
}

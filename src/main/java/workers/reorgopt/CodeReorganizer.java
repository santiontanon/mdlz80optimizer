/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import cl.MDLConfig;
import code.CodeBase;
import code.SourceStatement;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import workers.MDLWorker;
import workers.SourceCodeGenerator;

/**
 *
 * @author santi
 */
public class CodeReorganizer implements MDLWorker {

    MDLConfig config;
    
    String htmlOutputFileName = null;
    boolean trigger = false;
    
    public CodeReorganizer(MDLConfig a_config)
    {
        config = a_config;
    }
    
    @Override
    public String docString() {
        return "  -ro: (task) runs the reoganizer optimizer.\n" + 
               "  -rohtml <file>: generates a visualization of the reorganization process as an html file.\n";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-ro")) {
            flags.remove(0);
            trigger = true;
            return true;
        } else if (flags.get(0).equals("-rohtml") && flags.size()>=2) {
            flags.remove(0);
            htmlOutputFileName = flags.remove(0);
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
        // First, find the "top blocks" that we can work with, e.g. "pages in a MegaROM".
        // These are areas of code such that we can move things around inside, but not across.
        List<CodeBlock> topBlocks = new ArrayList<>();
        if (config.dialectParser != null) {
            config.dialectParser.getTopBlocks(code, topBlocks);
        } else {
            if (code.getMain() != null && !code.getMain().getStatements().isEmpty()) {
                CodeBlock top = new CodeBlock("TB0", code.getMain().getStatements().get(0), null, code);
                topBlocks.add(top);
            }
        }
        
        // Within each "top block", identify blocks of code (there might be code mixed with data in each
        // top block), so, we tease appart each of the contiguous areas of code:
        // Note: each time tehre is a directive like "org", we need to start a new block, since
        // we cannot safely assume we can move code that is under one "org" to an area of the code that
        // is under a different "org". We call these the "top code blocks"
        for(CodeBlock topBlock: topBlocks) {
            List<CodeBlock> topCodeBlocks = findTopCodeBlocks(topBlock, code);
            if (topCodeBlocks == null) return false;
            
            for(CodeBlock block: topCodeBlocks) {
                // Within each "top code block", we can now re-organize code at will:
                if (block.type == CodeBlock.TYPE_CODE) {
                    reorganizeBlock(block, code);
                }
            }
        }
        
        if (htmlOutputFileName != null) {
            if (!writeOutputToHTML(topBlocks, code, htmlOutputFileName)) return false;
        }
                
        return true;
    }

    
    @Override
    public MDLWorker cloneForExecutionQueue() {
        CodeReorganizer w = new CodeReorganizer(config);

        w.htmlOutputFileName = htmlOutputFileName;
        
        // reset state:
        trigger = false;
        
        return w;
    }
    

    private List<CodeBlock> findTopCodeBlocks(CodeBlock topBlock, CodeBase code) {
        List<CodeBlock> blocks = topBlock.subBlocks;
        CodeBlock block = null;
        int idx_code = 0;
        int idx_data = 0;
        int state = CodeBlock.TYPE_UNKNOWN;  // 0: no code, no data, 1: code, 2: data

        for(SourceStatement s:topBlock.statements) {
            boolean startNewBlock = false;
            int newState = state;
            if (block == null) block = new CodeBlock(null, s);
            switch (s.type) {
                case SourceStatement.STATEMENT_CPUOP:
                    // code:
                    if (state == CodeBlock.TYPE_DATA) {
                        // change of block type, we need to look back for a label, and start a new block:
                        startNewBlock = true;
                    } else {
                        block.statements.add(s);
                    }   newState = CodeBlock.TYPE_CODE;
                    break;
                case SourceStatement.STATEMENT_DATA_BYTES:
                case SourceStatement.STATEMENT_DATA_WORDS:
                case SourceStatement.STATEMENT_DATA_DOUBLE_WORDS:
                case SourceStatement.STATEMENT_INCBIN:
                    // data:
                    if (state == CodeBlock.TYPE_CODE) {
                        // change of block type, we need to look back for a label, and start a new block:
                        startNewBlock = true;
                    } else {
                        block.statements.add(s);
                    }   newState = CodeBlock.TYPE_DATA;
                    break;
                case SourceStatement.STATEMENT_ORG:
                case SourceStatement.STATEMENT_DEFINE_SPACE:
                    startNewBlock = true;
                    newState = CodeBlock.TYPE_UNKNOWN;
                    break;
                case SourceStatement.STATEMENT_MACRO:
                case SourceStatement.STATEMENT_MACROCALL:
                    // we should not have found any of these:
                    config.error("CodeReorganizer: Found a statement of type " + s.type + " in findTopCodeBlocks.");
                    return null;
                default:
                    block.statements.add(s);
                    break;
            }             
            
            if (startNewBlock) {
                List<SourceStatement> moveToNewBlock = new ArrayList<>();
                if (!block.statements.isEmpty()) {                    
                    if (state == CodeBlock.TYPE_CODE) {
                        block.ID = topBlock.ID + "_C" + idx_code;
                        block.type = state;
                        idx_code++;
                    } else {
                        block.ID = topBlock.ID + "_D" + idx_data;
                        block.type = CodeBlock.TYPE_DATA;
                        idx_data++;
                    }
                    
                    if (s.label == null) {
                        boolean labelFound = false;
                        for(int i = block.statements.size()-1;i>=0;i--) {
                            if (block.statements.get(i).type == SourceStatement.STATEMENT_NONE) {
                                moveToNewBlock.add(0, block.statements.get(i));
                                if (block.statements.get(i).label != null) {
                                    labelFound = true;
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                        if (labelFound) {
                            for(SourceStatement s2:moveToNewBlock) {
                                block.statements.remove(s2);
                            }
                        } else {
                            moveToNewBlock.clear();
                        }
                    }
                    if (!block.statements.isEmpty()) blocks.add(block);
                }
                // start a new block:
                block = new CodeBlock(null, s);
                block.statements.addAll(moveToNewBlock);
                block.statements.add(s);                
            }
            state = newState;
        }
        
        if (!block.statements.isEmpty()) {
            if (state == CodeBlock.TYPE_CODE) {
                block.ID = topBlock.ID + "_C" + idx_code;
                block.type = state;
            } else {
                block.ID = topBlock.ID + "_D" + idx_data;
                block.type = CodeBlock.TYPE_DATA;
            }
            blocks.add(block);
        }
        
        return blocks;
    }
    

    private void reorganizeBlock(CodeBlock block, CodeBase code) {
        // ...
    }

    private boolean writeOutputToHTML(List<CodeBlock> topBlocks, CodeBase code, String htmlOutputFileName) {
        SourceCodeGenerator generator = new SourceCodeGenerator(config);
        
        try (FileWriter fw = new FileWriter(htmlOutputFileName)) {
            // write HTML header:
            fw.write("<head>\n");            
            fw.write("<title>MDL: code reorganizer</title>\n");
            fw.write("<style>\n");
            fw.write("  table.noblock {\n");
            fw.write("    border-collapse: collapse;\n");
            fw.write("    border: none;\n");
            fw.write("    border-top: none;\n");
            fw.write("    border-bottom: none;");
            fw.write("  }\n");
            fw.write("  table.topblock {\n");
            fw.write("    border: 1px solid black;\n");
            fw.write("    background-color: #eeeeee;\n");
            fw.write("    }\n");        
            fw.write("  table.innerblock {\n");
            fw.write("    border: 1px solid black;\n");
            fw.write("    background-color: #cccccc;\n");
            fw.write("  }\n");        
            fw.write("  table.codeblock {\n");
            fw.write("    border: 1px solid blue;\n");
            fw.write("    background-color: #ccccff;\n");
            fw.write("  }\n");        
            fw.write("  table.datablock {\n");
            fw.write("    border: 1px solid red;\n");
            fw.write("    background-color: #ffffcc;\n");
            fw.write("  }\n");        
            fw.write("</style>\n");
            fw.write("</head>\n");
            fw.write("<body>\n");
            
            for(CodeBlock topBlock:topBlocks) {
                fw.write("<table class=\"topblock\">\n");
                fw.write("<tr><th>"+topBlock.ID+"</th></tr>\n");
                fw.write("<tr><td>\n");
                if (topBlock.subBlocks.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    generator.sourceFileString(topBlock.statements, code, sb);
                    StringTokenizer st = new StringTokenizer(sb.toString(), "\n");

                    while(st.hasMoreTokens()) {
                        String line = st.nextToken();
                        fw.write("<tr><td><pre>"+line+"</pre></td></tr>\n");
                    }
                } else {
                    if (!writeOutputToHTMLInnerBlock(topBlock.subBlocks, code, fw, generator)) return false;
                }               
                fw.write("</td></tr>\n");
                fw.write("</table>\n");                    
            }
            
            // finish HTML doc:
            fw.write("</body>\n");
            fw.flush();
        } catch (Exception e) {        
            config.error("Cannot write to file " + htmlOutputFileName + ": " + e);
            config.error(Arrays.toString(e.getStackTrace()));
            return false;
        }
        return true;
    }
    
    
    private boolean writeOutputToHTMLInnerBlock(List<CodeBlock> blocks, CodeBase code, FileWriter fw, SourceCodeGenerator generator) throws Exception
    {
        for(CodeBlock block:blocks) {
            if (block.subBlocks.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                generator.sourceFileString(block.statements, code, sb);
                StringTokenizer st = new StringTokenizer(sb.toString(), "\n");

                if (block.type == CodeBlock.TYPE_CODE) {
                    fw.write("<table class=\"codeblock\">\n");
                } else {
                    fw.write("<table class=\"datablock\">\n");
                }
                fw.write("<tr><th>"+block.ID+"</th></tr>\n");
                while(st.hasMoreTokens()) {
                    String line = st.nextToken();
                    fw.write("<tr><td><pre>"+line+"</pre></td></tr>\n");
                }
                fw.write("</table>\n");                    
            } else {
                fw.write("<table class=\"innerblock\">\n");
                fw.write("<tr><th>"+block.ID+"</th></tr>\n");
                fw.write("<tr><td>\n");
                if (!writeOutputToHTMLInnerBlock(block.subBlocks, code, fw, generator)) return false;
                fw.write("</td></tr>\n");
                fw.write("</table>\n");                    
            }               
        }      
        return true;
    }
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.commons.lang3.tuple.Pair;
import workers.MDLWorker;
import workers.SourceCodeGenerator;

/**
 *
 * @author santi
 * 
 * Throughout this file, I use the following names to identify the different types of blocks:
 * - areas: a given assembler program might have different "areas" that are
 *          separate, and where we should not move anything from one area into
 *          the other. For example, different pages in an MSX MegaROM.
 * - sub-areas: a given area might be sub-divided into blocks that, even if in principle
 *              we should be able to move around, the programmer might be assuming that
 *              things remain within those sub-areas. Thus, we should not move things
 *              from one of those sub-areas to another. For example, when there is an "org"
 *              statement, we assume the programmer wants that part of the code to start at
 *              that specific address and not somewhere else.
 * - code blocks: basic primitive sequences of assembler code that start with a label and end 
 *                with either an unconditional jump or a ret.
 * 
 */
public class CodeReorganizer implements MDLWorker {
    
    public static class ReorganizerStats {
        int moves = 0;
        int bytesSaved = 0;
        int timeSavings[] = {0, 0};
        
        void addMoveSavings(int a_bytesSaved, int[] a_timeSavings) {
            moves++;
            bytesSaved += a_bytesSaved;
            timeSavings[0] += a_timeSavings[0];
            if (a_timeSavings.length == 2) {
                timeSavings[1] += a_timeSavings[1];
            } else {
                timeSavings[1] += a_timeSavings[0];
            }
        }
        
        String timeSavingsString()
        {
            if (timeSavings[0] == timeSavings[1]) {
                return "" + timeSavings[0];
            } else {
                return "" + timeSavings[0] + "/" + timeSavings[1];
            }
        }
    }
    
    
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
               "  -rohtml <file>: generates a visualization of the division of the code before optimization as an html file.\n";
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
        ReorganizerStats savings = new ReorganizerStats();

        // First, find the "areas" that we can work with, e.g. "pages in a MegaROM".
        // These are areas of code such that we can move things around inside, but not across.
        List<CodeBlock> areas = new ArrayList<>();
        if (config.dialectParser != null) {
            config.dialectParser.getBlockAreas(code, areas);
        } else {
            if (code.getMain() != null && !code.getMain().getStatements().isEmpty()) {
                CodeBlock top = new CodeBlock("TB0", CodeBlock.TYPE_UNKNOWN, code.getMain().getStatements().get(0), null, code);
                areas.add(top);
            }
        }
        
        // Within each "area", identify sub-areas of code (there might be code mixed with data in each
        // area), so, we tease appart each of the contiguous areas of code:
        // Note: each time tehre is a directive like "org", we need to start a new sub-area, since
        // we cannot safely assume we can move code that is under one "org" to an area of the code that
        // is under a different "org". We call these the "sub-areas"
        for(CodeBlock area: areas) {
            if (findSubAreas(area, code) == null) return false;
            for(CodeBlock subarea: area.subBlocks) {
                // Within each "sub-area", we can now re-organize code at will:                
                if (subarea.type == CodeBlock.TYPE_CODE) {
                    // find all the blocks:
                    findCodeBlocks(subarea);
                    constructFlowGraph(subarea.subBlocks);
                }
            }
        }
        
        if (htmlOutputFileName != null) {
            if (!writeOutputToHTML(areas, code, htmlOutputFileName)) return false;
        }

        // optimize:
        for(CodeBlock area: areas) {
            for(CodeBlock subarea: area.subBlocks) {
                // Within each "sub-area", we can now re-organize code at will:                
                if (subarea.type == CodeBlock.TYPE_CODE) {
                    reorganizeBlock(subarea, code, savings);
                }
            }
        }
                
        config.info("CodeReorganizer: "+savings.moves+" moves applied, " +
            savings.bytesSaved + " bytes, " + 
            savings.timeSavingsString() + " " +config.timeUnit+"s saved.");

                
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
    

    private List<CodeBlock> findSubAreas(CodeBlock topBlock, CodeBase code) {
        List<CodeBlock> blocks = topBlock.subBlocks;
        CodeBlock block = null;
        int idx_code = 0;
        int idx_data = 0;
        int state = CodeBlock.TYPE_UNKNOWN;  // 0: no code, no data, 1: code, 2: data

        for(SourceStatement s:topBlock.statements) {
            boolean startNewBlock = false;
            int newState = state;
            if (block == null) block = new CodeBlock(null, CodeBlock.TYPE_UNKNOWN, s);
            switch (s.type) {
                case SourceStatement.STATEMENT_CPUOP:
                    // code:
                    if (state == CodeBlock.TYPE_DATA) {
                        // change of block type, we need to look back for a label, and start a new block:
                        startNewBlock = true;
                    } else {
                        block.statements.add(s);
                    }
                    newState = CodeBlock.TYPE_CODE;
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
                    }
                    newState = CodeBlock.TYPE_DATA;
                    break;
                case SourceStatement.STATEMENT_ORG:
                    if (s.org.type == Expression.EXPRESSION_SUM &&
                        s.org.args.size() == 2 &&
                        s.org.args.get(0).type == Expression.EXPRESSION_SYMBOL &&
                        s.org.args.get(0).symbolName.equals(CodeBase.CURRENT_ADDRESS) &&
                        s.org.args.get(1).evaluatesToIntegerConstant() &&
                        !s.org.args.get(1).containsLabel(code)) {
                        // this is fine, it's just like data:
                        if (state == CodeBlock.TYPE_CODE) {
                            // change of block type, we need to look back for a label, and start a new block:
                            startNewBlock = true;
                        } else {
                            block.statements.add(s);
                        }
                        newState = CodeBlock.TYPE_DATA;
                    } else {
                        startNewBlock = true;
                        newState = CodeBlock.TYPE_UNKNOWN;
                    }
                    break;
                case SourceStatement.STATEMENT_DEFINE_SPACE:
                    if (s.space_value != null ||
                        (s.space.evaluatesToIntegerConstant() &&
                         !s.space.containsLabel(code) &&
                         !s.space.containsCurrentAddress())) {
                        // this is fine, it's just like data:
                        if (state == CodeBlock.TYPE_CODE) {
                            // change of block type, we need to look back for a label, and start a new block:
                            startNewBlock = true;
                        } else {
                            block.statements.add(s);
                        }
                        newState = CodeBlock.TYPE_DATA;
                    } else {
                        // otherwise, this is like an "org"
                        startNewBlock = true;
                        newState = CodeBlock.TYPE_UNKNOWN;
                    }
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
                block = new CodeBlock(null, CodeBlock.TYPE_UNKNOWN, s);
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
    

    // Assumption: all the statements within this subarea contain assembler code, and not data
    private void reorganizeBlock(CodeBlock subarea, CodeBase code, ReorganizerStats savings) {
        // Look for blocks (A) that:
        // - end in a jump to a block B
        // - all the incoming edges to A and B are jumps
        // If any of these are found, B can be moved to before A, saving a jump
        for(CodeBlock block:subarea.subBlocks) {
            CodeBlock target = null;
            boolean safe = true;
            if (block.outgoing.size() == 1) {
                BlockFlowEdge edge = block.outgoing.get(0);
                if (edge.type == BlockFlowEdge.TYPE_UNCONDITIONAL_JP ||
                    edge.type == BlockFlowEdge.TYPE_UNCONDITIONAL_JR) {
                    target = edge.target;
                }
            }
            if (target != null && target != block) {
                for(BlockFlowEdge edge:block.incoming) {
                    if (edge.type == BlockFlowEdge.TYPE_NONE) {
                        safe = false;
                        break;
                    }
                }
                for(BlockFlowEdge edge:target.incoming) {
                    if (edge.type == BlockFlowEdge.TYPE_NONE) {
                        safe = false;
                        break;
                    }
                }
                if (safe) {
                    config.debug("Potential optimization: move " + block.ID + " to just before " + target.ID + " or " + target.ID + " right after " + block.ID);

                    // Try the optimization:
                    if (!attemptBlockMove(block, target, true, subarea, code, savings)) {
                        attemptBlockMove(target, block, false, subarea, code, savings);
                    }
                }
            }
        }
    }
    
    
    private boolean attemptBlockMove(CodeBlock toMove, CodeBlock destination, boolean moveBefore, CodeBlock subarea, CodeBase code, ReorganizerStats savings)
    {
        // Move the statements:
        SourceFile insertionFile;
        int insertionPoint;
        
        List<Pair<SourceStatement, Pair<SourceFile, Integer>>> undoTrail = new ArrayList<>();
        for(SourceStatement s: toMove.statements) {
            SourceFile undoFile = s.source;
            int undoPoint = s.source.getStatements().indexOf(s);
            undoTrail.add(0, Pair.of(s, Pair.of(undoFile, undoPoint)));
            s.source.getStatements().remove(s);
            if (undoPoint == -1) {
                config.error("CodeReorganizer: attemptBlockMove could not construct undo trail!");
                return false;                
            }
        }
        
        if (moveBefore) {
            insertionFile = destination.statements.get(0).source;
            insertionPoint = insertionFile.getStatements().indexOf(destination.statements.get(0));
            if (insertionPoint == -1) {
                config.error("CodeReorganizer: attemptBlockMove(moveBefore) cannot find insertionPoint");
                return false;
            }
        } else {
            insertionFile = destination.statements.get(destination.statements.size()-1).source;
            insertionPoint = insertionFile.getStatements().indexOf(destination.statements.get(destination.statements.size()-1))+1;
            if (insertionPoint == -1) {
                config.error("CodeReorganizer: attemptBlockMove(moveAfter) cannot find insertionPoint");
                return false;
            }
        }
                    
        for(SourceStatement s: toMove.statements) {
            insertionFile.addStatement(insertionPoint, s);
            s.source = insertionFile;
            insertionPoint++;
        }
        
        // Check all relative jumps are still within reach:
        boolean relativeJumpsInRange = true;
        code.resetAddresses();
        for(SourceStatement s: subarea.statements) {
            if (s.type == SourceStatement.STATEMENT_CPUOP) {
                if (s.op.isJump()) {
                    if (!s.op.labelInRange(s, code)) {
                        relativeJumpsInRange = false;
                        break;
                    }
                }
            }
        }
        

        // if they are not, undo the optimization:
        if (!relativeJumpsInRange) {
            for(Pair<SourceStatement, Pair<SourceFile, Integer>> undo: undoTrail) {
                SourceStatement s = undo.getLeft();
                SourceFile undoFile = undo.getRight().getLeft();
                int undoPoint = undo.getRight().getRight();
                s.source.getStatements().remove(s);
                undoFile.getStatements().add(undoPoint, s);
                s.source = undoFile;
            }
            return false;
        }

        // Remove the jump:
        SourceStatement jump = toMove.getLastCpuOpStatement();
        int bytesSaved = jump.op.sizeInBytes();
        int timeSaved[] = jump.op.timing();
        String timeSavedString = (timeSaved.length == 1 || timeSaved[0] == timeSaved[1] ?
                                    "" + timeSaved[0] :
                                    "" + timeSaved[0] + "/" + timeSaved[1]);
        jump.type = SourceStatement.STATEMENT_NONE;
        jump.comment = jump.op + "  ; -mdl";
        jump.op = null;
        code.resetAddresses();
        
        savings.addMoveSavings(bytesSaved, timeSaved);
        
        // Update the edges, and announce the optimization (with line ranges):        
        if (moveBefore) {
            for(BlockFlowEdge e:toMove.outgoing) {
                if (e.target == destination) {
                    e.type = BlockFlowEdge.TYPE_NONE;
                }
            }
            config.info("Reorganization optimization",
                    toMove.statements.get(0).sl.fileNameLineString(), 
                    "move lines " + toMove.statements.get(0).sl.lineNumber + " - " + 
                                    toMove.statements.get(toMove.statements.size()-1).sl.lineNumber + 
                    "to right before " + destination.statements.get(0).sl.fileNameLineString() + 
                    " ("+bytesSaved+" bytes, " + timeSavedString + " " + config.timeUnit+"s saved)");
        } else {
            for(BlockFlowEdge e:destination.outgoing) {
                if (e.target == toMove) {
                    e.type = BlockFlowEdge.TYPE_NONE;
                }
            }
            config.info("Reorganization optimization",
                    toMove.statements.get(0).sl.fileNameLineString(), 
                    "move lines " + toMove.statements.get(0).sl.lineNumber + " - " + 
                                    toMove.statements.get(toMove.statements.size()-1).sl.lineNumber + 
                    "to right after " + destination.statements.get(destination.statements.size()-1).sl.fileNameLineString() + 
                    " ("+bytesSaved+" bytes, " + timeSavedString + " " + config.timeUnit+"s saved)");
        }

        return true;
    }
    
    
    private List<CodeBlock> findCodeBlocks(CodeBlock subarea) {
        List<CodeBlock> codeBlocks = subarea.subBlocks;
        int idx = 0;
        CodeBlock block = null;
        for(SourceStatement s:subarea.statements) {
            if (block == null) {
                block = new CodeBlock(subarea.ID + "_B" + idx, CodeBlock.TYPE_CODE, s);
                idx++;
            }
            block.statements.add(s);
            if (s.type == SourceStatement.STATEMENT_CPUOP) {
                if (s.op.isRet() || s.op.isJump()) {
                    if (s.op.isConditional()) {
                        // conditional jump/ret:
                        // TODO: handle this case better (break into a block with two "next blocks")
                    } else {
                        // unconditional jump/ret: end of a block!
                        block.findStartLabel();
                        codeBlocks.add(block);
                        block = null;
                    }
                }
            }
        }
        if (block != null) {
            block.findStartLabel();
            codeBlocks.add(block);
        }        
        return codeBlocks;
    }    
    
    
    private void constructFlowGraph(List<CodeBlock> codeBlocks)
    {
        for(int i = 0;i<codeBlocks.size();i++) {
            CodeBlock block = codeBlocks.get(i);
            SourceStatement last = block.getLastCpuOpStatement();
            
            if (last != null) {
                if (last.op.isJump()) {
                    if (last.op.isConditional()) {
                        // TODO: handle this case
                        // ...
                    } else {
                        // Look for the destination block:
                        Expression target = last.op.getTargetJumpExpression();
                        if (target.type == Expression.EXPRESSION_SYMBOL) {
                            for(CodeBlock block2:codeBlocks) {
                                if (block2.label != null &&
                                    block2.label.name.equals(target.symbolName)) {
                                    // destination found!
                                    BlockFlowEdge edge = null;
                                    switch(last.op.spec.opName) {
                                        case "jp":
                                            edge = new BlockFlowEdge(block, block2, BlockFlowEdge.TYPE_UNCONDITIONAL_JP, null);
                                            break;
                                        case "jr":
                                            edge = new BlockFlowEdge(block, block2, BlockFlowEdge.TYPE_UNCONDITIONAL_JR, null);
                                            break;
                                        case "djnz":
                                            edge = new BlockFlowEdge(block, block2, BlockFlowEdge.TYPE_DJNZ, null);
                                            break;
                                        default:
                                            config.warn("CodeReorganizer: Unsupported jump type in " + last.op);
                                    }
                                    if (edge != null) {
                                        block.outgoing.add(edge);
                                        edge.target.incoming.add(edge);
                                    }
                                    break;
                                }
                            }
                        } else {
                            // Jumping to an expression, not yet handled:
                            // ...
                        }
                    }
                } else if (last.op.isRet()) {
                    // end of function, do not add any edge                    
                } else {
                    // no jump, no ret, this is just a fall through, so, it goes to the next block:
                    if (i < codeBlocks.size()) {
                        BlockFlowEdge edge = new BlockFlowEdge(block, codeBlocks.get(i+1), BlockFlowEdge.TYPE_NONE, null);
                        block.outgoing.add(edge);
                        edge.target.incoming.add(edge);
                    } else {
                        config.warn("Code safety", last.fileNameLineString(),
                                    "Assembler code finishes dangerously, and might continue executing into unexpected memory space.");                        
                    }
                }
            }
        }        
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
            fw.write("  .collapsible {\n");
            fw.write("    background-color: #777;\n");
            fw.write("    color: white;\n");
            fw.write("    cursor: pointer;\n");
            fw.write("    padding: 18px;\n");
            fw.write("    width: 100%;\n");
            fw.write("    border: none;\n");
            fw.write("    text-align: left;\n");
            fw.write("    outline: none;\n");
            fw.write("    font-size: 15px;\n");
            fw.write("  }\n");
            fw.write("  .active, .collapsible:hover {\n");
            fw.write("    background-color: #555;\n");
            fw.write("  }\n");
            fw.write("  .content {\n");
            fw.write("    padding: 0 18px;\n");
            fw.write("    display: block;\n");
            fw.write("    overflow: hidden;\n");
            fw.write("    background-color: #f1f1f1;\n");
            fw.write("  }\n");
            fw.write("</style>\n");
            fw.write("</head>\n");
            fw.write("<body>\n");
            
            for(CodeBlock topBlock:topBlocks) {
                fw.write("<table class=\"topblock\">\n");
                fw.write("<tr><td><button type=\"button\" class=\"collapsible\">"+topBlock.ID+"</button>\n");
                fw.write("<div class=\"content\">\n");
                if (topBlock.subBlocks.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    generator.sourceFileString(topBlock.statements, code, sb);
                    StringTokenizer st = new StringTokenizer(sb.toString(), "\n");
                    fw.write("<table class=\"topblock\">\n");
                    while(st.hasMoreTokens()) {
                        String line = st.nextToken();
                        fw.write("<tr><td><pre>"+line+"</pre></td></tr>\n");
                    }
                    fw.write("</table>\n");
                } else {
                    if (!writeOutputToHTMLInnerBlock(topBlock.subBlocks, code, fw, generator)) return false;
                }               
                fw.write("</div></td></tr>\n");
                fw.write("</table>\n");                    
            }
            
            // finish HTML doc:
            fw.write("<script>\n");
            fw.write("var coll = document.getElementsByClassName(\"collapsible\");\n");
            fw.write("var i;\n");
            fw.write("for (i = 0; i < coll.length; i++) {\n");
            fw.write("  coll[i].addEventListener(\"click\", function() {\n");
            fw.write("    this.classList.toggle(\"active\");\n");
            fw.write("    var content = this.nextElementSibling;\n");
            fw.write("    if (content.style.display === \"none\") {\n");
            fw.write("      content.style.display = \"block\";\n");
            fw.write("    } else {\n");
            fw.write("      content.style.display = \"none\";\n");
            fw.write("    }\n");
            fw.write("  });\n");
            fw.write("}\n");
            fw.write("</script>\n");
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
                fw.write("<tr><td><button type=\"button\" class=\"collapsible\">"+block.ID+"</button>\n");
                fw.write("<div class=\"content\">\n");
                if (block.type == CodeBlock.TYPE_CODE) {
                    fw.write("<table class=\"codeblock\">\n");
                } else {
                    fw.write("<table class=\"datablock\">\n");
                }
                while(st.hasMoreTokens()) {
                    String line = st.nextToken();
                    fw.write("<tr><td><pre>"+line+"</pre></td></tr>\n");
                }
                fw.write("</table>\n");                    
                fw.write("</div></td></tr>\n");
                fw.write("</table>\n");                    
            } else {
                fw.write("<table class=\"innerblock\">\n");
                fw.write("<tr><td><button type=\"button\" class=\"collapsible\">"+block.ID+"</button>\n");
                fw.write("<div class=\"content\">\n");
                if (!writeOutputToHTMLInnerBlock(block.subBlocks, code, fw, generator)) return false;
                fw.write("</div></td></tr>\n");
                fw.write("</table>\n");                    
            }               
        }      
        return true;
    }    
}

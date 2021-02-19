/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.reorgopt;

import cl.MDLConfig;
import cl.OptimizationResult;
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
    public static final String SAVINGS_REORGANIZATIONS_CODE = "CodeReorganizer moves";
    
    
    MDLConfig config;
    
    String htmlOutputFileName = null;
    boolean trigger = false;
    
    public CodeReorganizer(MDLConfig a_config)
    {
        config = a_config;
    }
    
    @Override
    public String docString() {
        return "  -ro: (task) runs the code reoganizer optimizer.\n" + 
               "  -rohtml <file>: generates a visualization of the division of the code before code reoganizer optimization as an html file.\n";
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
        OptimizationResult savings = new OptimizationResult();
        savings.addOptimizerSpecific(SAVINGS_REORGANIZATIONS_CODE, 0);

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
                
        config.info(SAVINGS_REORGANIZATIONS_CODE + ": "+savings.optimizerSpecificStats.get(SAVINGS_REORGANIZATIONS_CODE)+", " +
            savings.bytesSaved + " bytes, " + 
            savings.timeSavingsString() + " " +config.timeUnit+"s saved.");

        config.optimizerStats.addSavings(savings);
        
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
        
        // Clean up the blocks a bit (for example, the comments at the top of a file might have been 
        // lumped together with the end of the previous file):
        for(int i = 0;i<blocks.size()-1;i++) {
            CodeBlock block1 = blocks.get(i);
            CodeBlock block2 = blocks.get(i+1);
            // find the last non empty/comment statement of block1:
            int block1_lastNonEmpty = -1;
            for(int j = block1.statements.size()-1;j>=0;j--) {
                if (block1.statements.get(j).type != SourceStatement.STATEMENT_NONE) {
                    block1_lastNonEmpty = j;
                    break;
                }
            }
            int block2_firstNonEmpty = -1;
            for(int j = 0;j<block2.statements.size();j++) {
                if (block2.statements.get(j).type != SourceStatement.STATEMENT_NONE) {
                    block2_firstNonEmpty = j;
                    break;
                }
            }
            if (block1_lastNonEmpty >= 0 && block2_firstNonEmpty >= 0) {
                SourceFile b1_source = block1.statements.get(block1_lastNonEmpty).source;
                SourceFile b2_source = block2.statements.get(block2_firstNonEmpty).source;
                if (b1_source != b2_source) {
                    if (block1.statements.get(block1.statements.size()-1).source != b1_source) {
                        List<SourceStatement> toMove = new ArrayList<>();
                        for(int j = block1.statements.size()-1; j > block1_lastNonEmpty; j--) {
                            SourceStatement s = block1.statements.get(j);
                            if (s.source == b2_source) {
                                toMove.add(s);
                            }
                        }
                        for(SourceStatement s:toMove) {
                            block1.statements.remove(s);
                            block2.statements.add(0, s);
                        }
                    }
                }
            } 
        }
        
        return blocks;
    }
    

    // Assumption: all the statements within this subarea contain assembler code, and not data
    private void reorganizeBlock(CodeBlock subarea, CodeBase code, OptimizationResult savings) {
        // Detect any potential jump tables and protect them from optimizations:
        // Look for series of "jp" instructions one oafter another, after a label:
        for(int i = 0;i<subarea.statements.size();i++) {
            if (subarea.statements.get(i).type == SourceStatement.STATEMENT_NONE && 
                subarea.statements.get(i).label != null) {
                // we found a label:
                if (subarea.statements.size() > i + 2) {
                    if (subarea.statements.get(i+1).type == SourceStatement.STATEMENT_CPUOP &&
                        subarea.statements.get(i+1).op.isJump() && 
                        !subarea.statements.get(i+1).op.isConditional() &&
                        subarea.statements.get(i+2).type == SourceStatement.STATEMENT_CPUOP &&
                        subarea.statements.get(i+2).op.isJump() && 
                        !subarea.statements.get(i+2).op.isConditional()) {
                        // we found a jump table, protect it:
                        SourceStatement s = subarea.statements.get(i);
                        if (s.comment == null) {
                            s.comment = "; mdl:no-opt (mdl suspects this is a jump table)";
                        } else {
                            s.comment += "  ; mdl:no-opt (mdl suspects this is a jump table)";                                    
                        }
                        i++;
                        while(i<subarea.statements.size()) {
                            s = subarea.statements.get(i);
                            if (s.type == SourceStatement.STATEMENT_CPUOP &&
                                s.op.isJump() && 
                                !s.op.isConditional()) {
                                if (s.comment == null) {
                                    s.comment = "; mdl:no-opt (mdl suspects this is a jump table)";
                                } else {
                                    s.comment += "  ; mdl:no-opt (mdl suspects this is a jump table)";                                    
                                }
                            } else {
                                break;
                            }
                            i++;
                        }
                    }
                }
            }
        }
        
        // Look for blocks (A) that:
        // - end in a jump to a block B
        // - all the incoming edges to A and B are jumps
        // If any of these are found, B can be moved to before A, saving a jump
        boolean anyMove;
        do{
            anyMove = false;    // if we need to do another iteration, set anyMove to true
            for(int i = 0;i<subarea.subBlocks.size();i++) {
                CodeBlock block = subarea.subBlocks.get(i);
                CodeBlock target = null;
                boolean safeToMoveBlock = true;
                boolean safeToMoveTarget = true;
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
                            safeToMoveBlock = false;  // the block before "block" expects block to be there, so we cannot move it
                            break;
                        }
                    }
                    for(BlockFlowEdge edge:target.incoming) {
                        if (edge.type == BlockFlowEdge.TYPE_NONE) {
                            safeToMoveTarget = false;   // the block before "target" expects block to be there, so we cannot move it
                            safeToMoveBlock = false;  // the block before "target" expects nothing to be in between, so, we cannot move block there either
                            break;
                        }
                    }
                    for(BlockFlowEdge edge:target.outgoing) {
                        if (edge.type == BlockFlowEdge.TYPE_NONE) {
                            safeToMoveTarget = false;   // the block after "target" expects target to be there, so we cannot move it
                            break;
                        }
                    }
                    if (safeToMoveBlock && block != subarea.subBlocks.get(0)) {
                        config.debug("Potential optimization: move " + block.ID + " to just before " + target.ID);
                        if (attemptBlockMove(block, target, true, subarea, code, savings)) {
                            anyMove = true;
                        }
                    }
                    if (!anyMove && safeToMoveTarget && target != subarea.subBlocks.get(0)) {
                        config.debug("Potential optimization: move " + target.ID + " to just after " + block.ID);
                        if (attemptBlockMove(target, block, false, subarea, code, savings)) {
                            anyMove = true;
                        }
                    }
                }
                if (anyMove) break;
            }
        }while(anyMove);
        
        // check if any local labels have been moved out of their contexts, and fix them:
        for(int i = 0;i<subarea.statements.size();i++) {
            SourceStatement s = subarea.statements.get(i);
            if (s.label != null && s.label.relativeTo != null) {
                // relative label!
                boolean found = false;
                SourceStatement s2 = s;
                while(s2 != null) {
                    if (s2.label != null && s2.label.relativeTo == null) {
                        // absolute label:
                        if (s.label.relativeTo == s2.label) {
                            found = true;
                        }
                        break;
                    }
                    s2 = s2.source.getPreviousStatementTo(s2, code);
                }
                if (!found) {
                    // we found a local label out of context!
                    config.debug("CodeReorganizer: local label out of context! " + s.label.originalName + " should be right after " + s.label.relativeTo.originalName + " but isn't!");
                    
                    // turn the local label into an absolute label:
                    s.label.relativeTo = null;
                    s.label.originalName = s.label.name;
                    
                    // reset the loop: (santi: this is an ugly hack, I know...)
                    i = -1;
                }
            }
        }
    }
    
    
    private boolean attemptBlockMove(CodeBlock toMove, CodeBlock destination, boolean moveBefore, CodeBlock subarea, CodeBase code, OptimizationResult savings)
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
                config.error("CodeReorganizer: attemptBlockMove could not construct undo trail! statement '" + s + "' (originally in "+s.sl.fileNameLineString()+") is not present in its source file " + s.source.fileName );
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
            if (toMove.outgoing.size() != 1) {
                config.error("CodeReorganizer: attemptBlockMove(moveBefore) moving a block with more than 1 outgoing edge not yet supported");
                return false;
            }            
        } else {
            insertionFile = destination.statements.get(destination.statements.size()-1).source;
            insertionPoint = insertionFile.getStatements().indexOf(destination.statements.get(destination.statements.size()-1))+1;
            if (insertionPoint == -1) {
                config.error("CodeReorganizer: attemptBlockMove(moveAfter) cannot find insertionPoint");
                return false;
            }
            if (destination.outgoing.size() != 1) {
                config.error("CodeReorganizer: attemptBlockMove(moveAfter) moving a block after a destination with more than 1 outgoing edge not yet supported");
                return false;
            }            
        }
                    
        for(SourceStatement s: toMove.statements) {
            insertionFile.addStatement(insertionPoint, s);
            s.source = insertionFile;
            insertionPoint++;
        }
        
        // Check all relative jumps are still within reach:
        boolean canncelOptimization = false;
        code.resetAddresses();
        for(SourceStatement s: subarea.statements) {
            if (s.type == SourceStatement.STATEMENT_CPUOP) {
                if (s.op.isJump()) {
                    if (!s.op.labelInRange(s, code)) {
                        canncelOptimization = true;
                        break;
                    }
                }
            }
        }
        
        // Get the jump we should remove:
        SourceStatement jump;
        if (moveBefore) {
            jump = toMove.getLastCpuOpStatement();
        } else {
            jump = destination.getLastCpuOpStatement();
        }
        
        if (jump.comment != null && jump.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) {
            config.debug("CodeReorganizer: canceling optimization due to a " + config.PRAGMA_NO_OPTIMIZATION + " directive");
            canncelOptimization = true;
        }

        // if they are not, undo the optimization:
        if (canncelOptimization) {
            for(SourceStatement s: toMove.statements) {
                insertionFile.getStatements().remove(s);
            }
            for(Pair<SourceStatement, Pair<SourceFile, Integer>> undo: undoTrail) {
                SourceStatement s = undo.getLeft();
                SourceFile undoFile = undo.getRight().getLeft();
                int undoPoint = undo.getRight().getRight();
                s.source.getStatements().remove(s);
                undoFile.getStatements().add(undoPoint, s);
                s.source = undoFile;
            }
            code.resetAddresses();            
            return false;
        }

        int bytesSaved = jump.op.sizeInBytes();
        int timeSaved[] = jump.op.timing();
        String timeSavedString = (timeSaved.length == 1 || timeSaved[0] == timeSaved[1] ?
                                    "" + timeSaved[0] :
                                    "" + timeSaved[0] + "/" + timeSaved[1]);
        jump.type = SourceStatement.STATEMENT_NONE;
        jump.comment = "; " + jump.sl.line + "  ; -mdl";
        jump.op = null;
        code.resetAddresses();
        
        savings.addSavings(bytesSaved, timeSaved);
        savings.addOptimizerSpecific(SAVINGS_REORGANIZATIONS_CODE, 1);
        
        // Update the edges, and announce the optimization (with line ranges):        
        if (moveBefore) {            
            config.info("Reorganization optimization",
                    toMove.statements.get(0).sl.fileNameLineString(), 
                    "move lines " + toMove.statements.get(0).sl.lineNumber + " - " + 
                                    toMove.statements.get(toMove.statements.size()-1).sl.lineNumber + 
                    " to right before " + destination.statements.get(0).sl.fileNameLineString() + 
                    " to remove a jump statement ("+bytesSaved+" bytes, " + timeSavedString + " " + config.timeUnit+"s saved)");
            
            // merge blocks:
            destination.ID = toMove.ID + "+" + destination.ID;
            destination.incoming = toMove.incoming;
            destination.label = toMove.label;
            destination.startStatement = toMove.startStatement;
            destination.statements.addAll(0, toMove.statements);
            subarea.subBlocks.remove(toMove);
            
            // clear any edges to the old "destination" block:
            for(CodeBlock block:subarea.subBlocks) {
                List<BlockFlowEdge> toDelete = new ArrayList<>();
                for(BlockFlowEdge e:block.outgoing) {
                    if (e.target == destination) toDelete.add(e);
                }
                for(BlockFlowEdge e:toDelete) {
                    block.outgoing.remove(e);
                }
            }
        } else {
            config.info("Reorganization optimization",
                    toMove.statements.get(0).sl.fileNameLineString(), 
                    "move lines " + toMove.statements.get(0).sl.lineNumber + " - " + 
                                    toMove.statements.get(toMove.statements.size()-1).sl.lineNumber + 
                    " to right after " + destination.statements.get(destination.statements.size()-1).sl.fileNameLineString() + 
                    " ("+bytesSaved+" bytes, " + timeSavedString + " " + config.timeUnit+"s saved)");
            
            // merge blocks:
            destination.ID = destination.ID + "+" + toMove.ID;
            destination.statements.addAll(toMove.statements);
            destination.outgoing = toMove.outgoing;
            subarea.subBlocks.remove(toMove);
            
            // clear any edges to the old "toMove" block:
            for(CodeBlock block:subarea.subBlocks) {
                List<BlockFlowEdge> toDelete = new ArrayList<>();
                for(BlockFlowEdge e:block.outgoing) {
                    if (e.target == toMove) toDelete.add(e);
                }
                for(BlockFlowEdge e:toDelete) {
                    block.outgoing.remove(e);
                }
            }
            
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
                    if (i < codeBlocks.size() - 1) {
                        BlockFlowEdge edge = new BlockFlowEdge(block, codeBlocks.get(i+1), BlockFlowEdge.TYPE_NONE, null);
                        block.outgoing.add(edge);
                        edge.target.incoming.add(edge);
                    } else {
                        config.warn("Code safety", last.fileNameLineString(),
                                    "Assembler code finishes dangerously, and might continue executing into unexpected memory space.");
                        // create an edge anyway, to make sure we don't move this bock, in case
                        // the data following the code is actually assembler instructions encoded
                        // as data:
                        BlockFlowEdge edge = new BlockFlowEdge(block, null, BlockFlowEdge.TYPE_NONE, null);
                        block.outgoing.add(edge);
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

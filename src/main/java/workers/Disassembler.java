/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpSpec;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.commons.lang3.tuple.Pair;
import parser.SourceLine;
import util.Resources;

/**
 *
 * @author santi
 */
public class Disassembler implements MDLWorker {
    
    public static class DisassemblerAnnotation {
        public int address;
        public String type;
        public String argument;
        
        
        public DisassemblerAnnotation(int a_address, String a_type, String a_argument) {
            address = a_address;
            type = a_type;
            argument = a_argument;
        }
    }
    

    MDLConfig config = null;
    String hintsFileName = null;
    
    int startAddress = 0;
    int dataBytesPerLine = 16;
    int renderDataAs = Expression.RENDER_AS_8BITHEX;
    int maxOpSpecSizeInBytes = 1;
    
    boolean resolveAbsoluteAddressesToLabels = true;
    boolean resolveRelativeAddressesToLabels = true;
    boolean removeUnusedLabels = true;
    boolean keepUserDefinedLabels = true;
    boolean moveLabelsToTheirOwnLines = true;
    
    List<String> userDefinedLabels = new ArrayList<>();
    
    // Disassembler state:
    int state_currentBlockAddress = 0;
    List<Integer> state_currentBlock = new ArrayList<>();
    boolean state_inCodeBlock = false;  // default to data
    int state_nextStatementLabelAddress = 0;
    String state_nextStatementLabel = null;
    int state_nextStatementCommentAddress = 0;
    String state_nextStatementComment = null;
    
    
    public Disassembler(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-da data/code/<input hints>```: disassembles the input binary. If this flag is used, " +    
               "the input file is interpreted as a binary file. The argument of this clad can be either " +
               "```data``` (indicating that the binary file is to be interpreted as data), ```code``` (" +
               "indicating that the binary file is to be interpreted as code), or a path to an ```<input hints>``` " +
               "file, which is a text file that gives hints to MDL about what is code and what is data. " +
               "The hints file is mandatory. If you don't want to provide any hints, just point MDL to an " +
               "empty file. The <input hints> format is as follows. Each line can be one of:\n" +
               "    org <address>\n" +
               "    label <address> <label>\n" +
               "    comment <address> <comment>\n" +
               "    comment-before <address> <comment>\n" +
               "    data <address>\n" +
               "    code <address>\n";
    }

    
    @Override
    public String simpleDocString() {
        return "";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-da") && flags.size()>=2) {
            flags.remove(0);
            hintsFileName = flags.remove(0);
            config.codeSource = MDLConfig.CODE_FROM_DISASSEMBLY;
            return true;
        }
        return false;
    }

    
    @Override
    public boolean triggered() {
        return hintsFileName != null;
    }

    
    @Override
    public boolean work(CodeBase code) {
        // Read the binary data:
        byte data[];
        List<Pair<Integer,DisassemblerAnnotation>> annotations = new ArrayList<>();
        
        try {
            InputStream fis = Resources.asInputStream(config.inputFiles.get(0));
            data = fis.readAllBytes();
        } catch(Exception e) {
            config.error("Cannot read binary input file: " + config.inputFiles.get(0));
            return false;
        }
        config.info("Disassembler: read " + data.length + " bytes from binary.");
        
        // Parse the hints file:
        try {
            switch(hintsFileName) {
                case "data":
                    annotations.add(Pair.of(startAddress, new DisassemblerAnnotation(startAddress, "data", null)));
                    break;
                case "code":
                    annotations.add(Pair.of(startAddress, new DisassemblerAnnotation(startAddress, "code", null)));
                    break;
                default:
                {
                    BufferedReader br = new BufferedReader(Resources.asReader(hintsFileName));
                    while(true) {
                        String line = br.readLine();
                        if (line == null) break;
                        StringTokenizer st = new StringTokenizer(line, " \t");
                        String type = st.nextToken();
                        String addressToken = st.nextToken();
                        String restOfTheLine = (st.hasMoreTokens() ? st.nextToken("").strip():null);
                        Expression addressExp = config.expressionParser.parse(
                                config.tokenizer.tokenize(addressToken), null, null, code);
                        if (addressExp == null || !addressExp.evaluatesToNumericConstant()) {
                            config.error("Cannot evaluate " + addressToken + " to a memory address!");
                            return false;
                        }
                        int address = addressExp.evaluateToInteger(null, code, true);
                        if (type.equalsIgnoreCase("org")) {
                            startAddress = address;
                        } else {
                            annotations.add(Pair.of(address, 
                                                    new DisassemblerAnnotation(address, type, restOfTheLine)));
                        }
                    }
                    break;
                }
            }
        } catch(Exception e) {
            config.error("Problem reading annotations file: " + hintsFileName);
            config.error(e.getMessage());
            config.error(Arrays.toString(e.getStackTrace()));
            return false;
        }

        SourceFile sf = new SourceFile("disassembled.asm", null, null, code, config);
        code.addSourceFile(sf);
        code.addOutput(null, sf, 0);
        
        for(CPUOpSpec spec:config.opParser.getOpSpecs()) {
            if (spec.sizeInBytes > maxOpSpecSizeInBytes) {
                maxOpSpecSizeInBytes = spec.sizeInBytes;
            }
        }
        
        // Disassemble!
        return disassemble(data, startAddress, annotations, sf, code);
    }


    public boolean disassemble(byte[] data,
                               int address,
                               List<Pair<Integer, DisassemblerAnnotation>> annotations,
                               SourceFile sf,
                               CodeBase code) {
        
        // Initialize the state:
        state_currentBlockAddress = address;
        state_currentBlock = new ArrayList<>();
        state_inCodeBlock = false;  // default to data
        state_nextStatementLabelAddress = 0;
        state_nextStatementLabel = null;
        state_nextStatementCommentAddress = 0;
        state_nextStatementComment = null;
        
        CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_ORG, new SourceLine("  org " + address, sf, sf.getStatements().size()), sf, config);
        s.org = Expression.constantExpression(address, Expression.RENDER_AS_16BITHEX, config);
        sf.addStatement(s);
        
        for(int i = 0; i<data.length;i++) {
            for(Pair<Integer, DisassemblerAnnotation> p:annotations) {
                if (p.getLeft().equals(address)) {
                    DisassemblerAnnotation a = p.getRight();
                    switch(a.type) {
                        case "label":
                            flushCurrentBlock(state_currentBlock, sf, code);
                            state_nextStatementLabel = a.argument;
                            state_nextStatementLabelAddress = address;
                            userDefinedLabels.add(state_nextStatementLabel);
                            break;

                        case "comment":
                            flushCurrentBlock(state_currentBlock, sf, code);
                            state_nextStatementComment = a.argument;
                            state_nextStatementCommentAddress = address;
                            break;

                        case "comment-before":
                        {
                            flushCurrentBlock(state_currentBlock, sf, code);
                            // Add a comment statement:
                            CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_NONE, new SourceLine("", sf, sf.getStatements().size()), sf, config);
                            s2.comment = a.argument;
                            sf.addStatement(s2);
                            break;
                        }

                        case "code":
                            config.debug("Code starting at " + address);
                            if (!state_inCodeBlock) {
                                flushCurrentBlock(state_currentBlock, sf, code);
                                state_inCodeBlock = true;
                            }
                            break;

                        case "data":
                            config.debug("Data starting at " + address);
                            if (state_inCodeBlock) {
                                flushCurrentBlock(state_currentBlock, sf, code);
                                state_inCodeBlock = false;
                            }
                            break;

                        default:
                            config.error("Unsupported disassembling annotation type: " + a.type);
                            return false;
                    }
                }
            }
            
            int v = (int)data[i];
            if (v<0) v += 256;
            state_currentBlock.add(v);
            address += 1;

            if (state_inCodeBlock) {
                tryToDisassembleInstruction(state_currentBlock, sf, code, false);
            } else if (state_currentBlock.size() >= dataBytesPerLine) {
                addDataStatement(state_currentBlock, sf, code);
            }            
        }

        // Any leftover data:
        flushCurrentBlock(state_currentBlock, sf, code);
        
        resolveJumpLabels(sf, code);
        if (removeUnusedLabels) removeUnusedLabels(sf, code);
        if (moveLabelsToTheirOwnLines) moveLabelsToTheirOwnLines(sf, code);
        
        return true;
    }
    
    
    public void flushCurrentBlock(List<Integer> currentBlock, SourceFile sf, CodeBase code)
    {
        if (!state_currentBlock.isEmpty()) {
            if (!state_inCodeBlock) {
                addDataStatement(state_currentBlock, sf, code);
            } else {
                tryToDisassembleInstruction(state_currentBlock, sf, code, true);
                // We add it as data, as if we have reached here,
                // it means we cannot disassemble it:
                if (!state_currentBlock.isEmpty()) {
                    config.info("Could not disassemble data to an instruction at #" + config.tokenizer.toHex(state_currentBlockAddress, 4));
                    addDataStatement(state_currentBlock, sf, code);
                }
            }
        }        
    }


    public int addDataStatement(List<Integer> currentBlock, SourceFile sf, CodeBase code) 
    {
        List<Integer> firstBlock = new ArrayList<>();
        for(int i = 0;i<dataBytesPerLine && !currentBlock.isEmpty();i++) {
            firstBlock.add(currentBlock.remove(0));
        }
        if (!firstBlock.isEmpty()) {
            CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_DATA_BYTES, new SourceLine("", sf, sf.getStatements().size()), sf, config);
            s.data = new ArrayList<>();
            for(Integer v:firstBlock) {
                s.data.add(Expression.constantExpression(v, renderDataAs, config));
            }
            if (state_currentBlockAddress == state_nextStatementLabelAddress &&
                state_nextStatementLabel != null) {
                addLabelToStatement(state_nextStatementLabel, s, code);
                state_nextStatementLabel = null;
            } else {
                addLabelToStatement("L" + config.tokenizer.toHex(state_currentBlockAddress, 4), s, code);
            }
            if (state_currentBlockAddress == state_nextStatementCommentAddress &&
                state_nextStatementComment != null) {
                s.comment = state_nextStatementComment;
                state_nextStatementComment = null;
            }
            sf.addStatement(s);
            state_currentBlockAddress += firstBlock.size();
        }
        if (!currentBlock.isEmpty()) {
            return addDataStatement(currentBlock, sf, code);
        }
        return state_currentBlockAddress;
    }

    
    public int tryToDisassembleInstruction(List<Integer> currentBlock, SourceFile sf, CodeBase code, boolean decodeBeforeWaitingForLongerInstructions) 
    {
        int maxOffset = currentBlock.size()-1;
        if (!decodeBeforeWaitingForLongerInstructions) {
            maxOffset = currentBlock.size() - maxOpSpecSizeInBytes;
        }
        
        for(int offset = 0;offset<=maxOffset;offset++) {
            for(CPUOpSpec spec:config.opParser.getOpSpecs()) {
                CPUOp op = spec.tryToDisassemble(currentBlock, offset, code);
                if (op != null) {
                    // We disassembled one instruction!
                    if (offset > 0) {
                        // insert a data block before for the part that was not disassembled:
                        List<Integer> dataBlock = new ArrayList<>();
                        for(int i = 0;i<offset;i++) {
                            dataBlock.add(currentBlock.remove(0));
                        }
                        config.info("Could not disassemble data to an instruction at #" + config.tokenizer.toHex(state_currentBlockAddress, 4));
                        addDataStatement(dataBlock, sf, code);
                    }
                    // insert the op spec:
                    CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_CPUOP, new SourceLine("", sf, sf.getStatements().size()), sf, config);
                    s.op = op;
                    if (state_currentBlockAddress == state_nextStatementLabelAddress &&
                        state_nextStatementLabel != null) {
                        addLabelToStatement(state_nextStatementLabel, s, code);
                        state_nextStatementLabel = null;
                    } else {
                        addLabelToStatement("L" + config.tokenizer.toHex(state_currentBlockAddress, 4), s, code);
                    }
                    if (state_currentBlockAddress == state_nextStatementCommentAddress &&
                        state_nextStatementComment != null) {
                        s.comment = state_nextStatementComment;
                        state_nextStatementComment = null;
                    }
                    sf.addStatement(s);
                    for(int i = 0;i<spec.sizeInBytes;i++) {
                        currentBlock.remove(0);
                        state_currentBlockAddress++;
                    }
                    return tryToDisassembleInstruction(currentBlock, sf, code, decodeBeforeWaitingForLongerInstructions);
                }
            }
        }
        
        return state_currentBlockAddress;
    }
    
    
    public void addLabelToStatement(String cName, CodeStatement s, CodeBase code)
    {
        SourceConstant c = new SourceConstant(cName, cName,
                                              Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), s, null);
        c.colonTokenUsedInDefinition = ":";
        s.label = c;
        code.addSymbol(c.name, c);
    }
    
    

    public void resolveJumpLabels(SourceFile sf, CodeBase code) {
        HashMap<Integer, SourceConstant> labelsMap = new HashMap<>();
        if (resolveAbsoluteAddressesToLabels || resolveRelativeAddressesToLabels) {
            // Cache all the label addresses:
            for(CodeStatement s:sf.getStatements()) {
                if (s.label != null) {
                    Integer address = s.getAddress(code);
                    if (address != null) {
                        labelsMap.put(address, s.label);
                    }
                }
            }
        }
        
        if (resolveAbsoluteAddressesToLabels) {
            for(CodeStatement s:sf.getStatements()) {
                if (s.op != null) {
                    if ((s.op.isJump() && !s.op.isRelativeJump()) || s.op.isCall()) {
                        Expression exp = s.op.getTargetJumpExpression();
                        if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                            SourceConstant label = labelsMap.get(exp.integerConstant);
                            if (label != null) {
                                exp.type = Expression.EXPRESSION_SYMBOL;
                                exp.symbolName = label.name;
                                if (label.definingStatement != null && label.definingStatement.type == CodeStatement.STATEMENT_DATA_BYTES) {
                                    config.info("Jump/call into a data block from "+s.label.name+": #" + config.tokenizer.toHex(exp.integerConstant, 4));
                                }
                            } else {
                                config.info("Could not resolve jump/call label from "+s.label.name+": #" + config.tokenizer.toHex(exp.integerConstant, 4));
                            }
                        }
                    }
                }
            }
        }
        if (resolveRelativeAddressesToLabels) {
            for(CodeStatement s:sf.getStatements()) {
                if (s.op != null) {
                    if (s.op.isRelativeJump()) {
                        Expression exp = s.op.getTargetJumpExpression();
                        if (exp.type != Expression.EXPRESSION_SYMBOL) {
                            Integer address = exp.evaluateToInteger(s, code, true);
                            if (address != null) {
                                SourceConstant label = labelsMap.get(address);
                                if (label != null) {
                                    exp.type = Expression.EXPRESSION_SYMBOL;
                                    exp.symbolName = label.name;
                                    if (label.definingStatement != null && label.definingStatement.type == CodeStatement.STATEMENT_DATA_BYTES) {
                                        config.info("Relative jump into a data block from "+s.label.name+": #" + config.tokenizer.toHex(address, 4));
                                    }
                                } else {
                                    config.info("Could not resolve relative jump label from "+s.label.name+": #" + config.tokenizer.toHex(address, 4));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    
    public void removeUnusedLabels(SourceFile sf, CodeBase code) {
        List<String> usedLabels = new ArrayList<>();
        
        if (keepUserDefinedLabels) {
            usedLabels.addAll(userDefinedLabels);    
        }
        
        // Find all the used symbols:
        for(CodeStatement s:sf.getStatements()) {
            if (s.op == null) continue;
            for(Expression exp:s.op.args) {
                exp.getAllSymbols(usedLabels);
            }
        }
        for(CodeStatement s:sf.getStatements()) {
            if (s.label != null) {
                if (!usedLabels.contains(s.label.name)) {
                    s.label = null;
                }
            }
        }
    }

    
    public void moveLabelsToTheirOwnLines(SourceFile sf, CodeBase code) {
        for(int i = 0;i<sf.getStatements().size();i++) {
            CodeStatement s = sf.getStatements().get(i);
            if (s.label != null) {
                CodeStatement s2 = new CodeStatement(CodeStatement.STATEMENT_NONE, new SourceLine("", sf, sf.getStatements().size()), sf, config);
                s2.label = s.label;
                s.label = null;
                sf.addStatement(i, s2);
            }
        }
    }
    
}

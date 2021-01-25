/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpSpec;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.StringTokenizer;
import parser.LineParser;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class SjasmDialect implements Dialect {
    
    public static class SjasmStruct {
        String name;
        SourceFile file;
        SourceStatement start;
        List<String> attributeNames = new ArrayList<>();
        List<Integer> attributeSizes = new ArrayList<>();
    }
    
    
    public static class CodeBlock {
        SourceStatement startStatement;
        int page = -1;
        Expression address;
        int actualAddress = -1;
        List<SourceStatement> statements = new ArrayList<>();
        
        public CodeBlock(SourceStatement a_s, int a_page, Expression a_address)
        {
            startStatement = a_s;
            page = a_page;
            address = a_address;
        }
        
        public int size(CodeBase code)
        {
            int size = 0;
            for(SourceStatement s:statements) {
                size += s.sizeInBytes(code, false, true, false);
            }
            return size;
        }
    }
    
    
    public static class CodePage {
        public SourceStatement s;
        public Expression start;
        public Expression size;
        public List<CodeBlock> blocks = new ArrayList<>();
        
        public CodePage(SourceStatement a_s, Expression a_start, Expression a_size)
        {
            s = a_s;
            start = a_start;
            size = a_size;
        }
        
        
        public boolean addBlock(CodeBlock block, CodeBase code)
        {
            int spot = -1;
            if (block.address != null) {
                // check if it fits:
                int blockAddress = block.address.evaluateToInteger(block.startStatement, code, true);
                for(CodeBlock b2:blocks) {
                    if (b2.actualAddress < blockAddress + block.size(code) &&
                        blockAddress < b2.actualAddress + b2.size(code)) {
                        // overlap!
                        return false;
                    } else {
                        if (b2.actualAddress > blockAddress) {
                            spot = blocks.indexOf(b2);
                        }
                    }
                }
                block.actualAddress = blockAddress;
                if (spot == -1) {
                    blocks.add(block);                
                } else {
                    blocks.add(spot, block);
                }
                return true;
            } else {            
                // otherwise, find a spot:
                int pageStart = start.evaluateToInteger(s, code, true);
                int blockAddress = pageStart;
                int blockSize = block.size(code);

                for(CodeBlock b2:blocks) {
                    if (b2.actualAddress-blockAddress >= blockSize) {
                        // found a spot!
                        spot = blocks.indexOf(b2);
                        break;
                    }
                    blockAddress = b2.actualAddress+b2.size(code);
                }
                if (spot == -1) {
                    // add at the end:
                    int spaceLeft = (pageStart + size.evaluateToInteger(s, code, false)) - blockAddress;
                    if (spaceLeft >= blockSize) {
                        block.actualAddress = blockAddress;
                        blocks.add(block);
                    } else {
                        return false;
                    }
                } else {
                    block.actualAddress = blockAddress;
                    blocks.add(spot, block);
                }
                return true;
            }
        }
    }
    

    MDLConfig config;

    SjasmStruct struct = null;
    List<SjasmStruct> structs = new ArrayList<>();

    int mapCounter = 0;
    List<Integer> mapCounterStack = new ArrayList<>();
    
    Integer currentPage = null;
    HashMap<Integer,CodePage> pages = new HashMap<>();
    HashMap<String,Integer> symbolPage = new HashMap<>();
    
    HashMap<String, Integer> reusableLabelCounts = new HashMap<>();

    List<CodeBlock> codeBlocks = new ArrayList<>();
    
    List<String> modules = new ArrayList<>();
    
    List<SourceStatement> enhancedJrList = new ArrayList<>();
    List<SourceStatement> enhancedDjnzList = new ArrayList<>();
    
    
    public SjasmDialect(MDLConfig a_config) {
        config = a_config;

        config.warning_jpHlWithParenthesis = false;  // I don't think sjasm supports "jp hl"
        
        config.lineParser.addKeywordSynonym("byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("dword", config.lineParser.KEYWORD_DD);
        config.lineParser.addKeywordSynonym("=", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym(".equ", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DS);
        
        config.preProcessor.macroSynonyms.put("endmacro", config.preProcessor.MACRO_ENDM);
        
        config.warning_jpHlWithParenthesis = false;
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.keywordsHintingALabel.add("#");
        config.lineParser.keywordsHintingALabel.add("field");
        config.lineParser.keywordsHintingALabel.add(":=");
        config.lineParser.allowIncludesWithoutQuotes = true;
        config.lineParser.macroDefinitionStyle = LineParser.MACRO_MACRO_NAME_ARGS;
        config.lineParser.allowNumberLabels = true;
        config.lineParser.allowExtendedSjasmInstructions = true;
        
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("high");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("low");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add(":");
                
        // We define it as a dialectMacro instead of as a synonym of "REPT", as it has some special syntax for
        // indicating the current iteration
        config.preProcessor.dialectMacros.put("repeat", "endrepeat");
        
        // It is important that registers on the left-hand-side are capitalized (the right hand side does not matter):
        addFakeInstruction("RL BC", "rl c\nrl b");
        addFakeInstruction("RL DE", "rl e\nrl d");
        addFakeInstruction("RL BC", "rl c\nrl b");
        addFakeInstruction("RL DE", "rl e\nrl d");
        addFakeInstruction("RL HL", "rl l\nrl h");
        addFakeInstruction("RR BC", "rr b\nrr c");
        addFakeInstruction("RR DE", "rr d\nrr e");
        addFakeInstruction("RR HL", "rr h\nrr l");
        addFakeInstruction("SLA BC", "sla c\nrl b");
        addFakeInstruction("SLA DE", "sla e\nrl d");
        addFakeInstruction("SLA HL", "add hl,hl");
        addFakeInstruction("SLL BC", "sli c\nrl b");
        addFakeInstruction("SLL DE", "sli e\nrl d");
        addFakeInstruction("SLL HL", "sli l\nrl h");
        addFakeInstruction("SLI BC", "sli c\nrl b");
        addFakeInstruction("SLI DE", "sli e\nrl d");
        addFakeInstruction("SLI HL", "sli l\nrl h");
        addFakeInstruction("SRA BC", "sra b\nrr c");
        addFakeInstruction("SRA DE", "sra d\nrr e");
        addFakeInstruction("SRA HL", "sra h\nrr l");
        addFakeInstruction("SRL BC", "srl b\nrr c");
        addFakeInstruction("SRL DE", "srl d\nrr e");
        addFakeInstruction("SRL HL", "srl h\nrr l");
        addFakeInstruction("LD BC,BC", "ld b,b\nld c,c");
        addFakeInstruction("LD BC,DE", "ld b,d\nld c,e");
        addFakeInstruction("LD BC,HL", "ld b,h\nld c,l");
        addFakeInstruction("LD BC,IX", "ld b,ixh\nld c,ixl");
        addFakeInstruction("LD BC,IY", "ld b,iyh\nld c,iyl");
        addFakeInstruction("LD BC,(HL)", "ld c,(hl)\ninc hl\nld b,(hl)\ndec hl");
        addFakeInstruction("LD DE,BC", "ld d,b\nld e,c");
        addFakeInstruction("LD DE,DE", "ld d,d\nld e,e");
        addFakeInstruction("LD DE,HL", "ld d,h\nld e,l");
        addFakeInstruction("LD DE,IX", "ld d,ixh\nld e,ixl");
        addFakeInstruction("LD DE,IY", "ld d,iyh\nld e,iyl");
        addFakeInstruction("LD DE,(HL)", "ld e,(hl)\ninc hl\nld d,(hl)\ndec hl");
        addFakeInstruction("LD HL,BC", "ld h,b\nld l,c");
        addFakeInstruction("LD HL,DE", "ld h,d\nld l,e");
        addFakeInstruction("LD HL,HL", "ld h,h\nld l,l");
        addFakeInstruction("LD HL,IX", "push ix\npop hl");
        addFakeInstruction("LD HL,IY", "push iy\npop hl");
        addFakeInstruction("LD IX,BC", "ld ixh,b\nld ixl,c");
        addFakeInstruction("LD IX,DE", "ld ixh,d\nld ixl,e");
        addFakeInstruction("LD IX,HL", "push hl\npop ix");
        addFakeInstruction("LD IX,IX", "ld ixh,ixh\nld ixl,ixl");
        addFakeInstruction("LD IX,IY", "push iy\npop ix");
        addFakeInstruction("LD IY,BC", "ld iyh,b\nld iyl,c");
        addFakeInstruction("LD IY,DE", "ld iyh,d\nld iyl,e");
        addFakeInstruction("LD IY,HL", "push hl\npop iy");
        addFakeInstruction("LD IY,IX", "push ix\npop iy");
        addFakeInstruction("LD IY,IY", "ld iyh,iyh\nld iyl,iyl");
        addFakeInstruction("LD (HL),BC", "ld (hl),c\ninc hl\nld (hl),b\ndec hl");
        addFakeInstruction("LD (HL),DE", "ld (hl),e\ninc hl\nld (hl),d\ndec hl");
        addFakeInstruction("LDI BC,(HL)", "ld c,(hl)\ninc hl\nld b,(hl)\ninc hl");
        addFakeInstruction("LDI DE,(HL)", "ld e,(hl)\ninc hl\nld d,(hl)\ninc hl");
        addFakeInstruction("LDI (HL),BC", "ld (hl),c\ninc hl\nld (hl),b\ninc hl");
        addFakeInstruction("LDI (HL),DE", "ld (hl),e\ninc hl\nld (hl),d\ninc hl");
        addFakeInstruction("LDI A,(BC)", "ld a,(bc)\ninc bc");
        addFakeInstruction("LDI A,(DE)", "ld a,(de)\ninc de");
        addFakeInstruction("LDI A,(HL)", "ld a,(hl)\ninc hl");
        addFakeInstruction("LDI B,(HL)", "ld b,(hl)\ninc hl");
        addFakeInstruction("LDI C,(HL)", "ld c,(hl)\ninc hl");
        addFakeInstruction("LDI D,(HL)", "ld d,(hl)\ninc hl");
        addFakeInstruction("LDI E,(HL)", "ld e,(hl)\ninc hl");
        addFakeInstruction("LDI H,(HL)", "ld h,(hl)\ninc hl");
        addFakeInstruction("LDI L,(HL)", "ld l,(hl)\ninc hl");
        addFakeInstruction("LDD A,(BC)", "ld a,(bc)\ndec bc");
        addFakeInstruction("LDD A,(DE)", "ld a,(de)\ndec de");
        addFakeInstruction("LDD A,(HL)", "ld a,(hl)\ndec hl");
        addFakeInstruction("LDD B,(HL)", "ld b,(hl)\ndec hl");
        addFakeInstruction("LDD C,(HL)", "ld c,(hl)\ndec hl");
        addFakeInstruction("LDD D,(HL)", "ld d,(hl)\ndec hl");
        addFakeInstruction("LDD E,(HL)", "ld e,(hl)\ndec hl");
        addFakeInstruction("LDD H,(HL)", "ld h,(hl)\ndec hl");
        addFakeInstruction("LDD L,(HL)", "ld l,(hl)\ndec hl");
        addFakeInstruction("LDI (BC),A", "ld (bc),a\ninc bc");
        addFakeInstruction("LDI (DE),A", "ld (de),a\ninc de");
        addFakeInstruction("LDI (HL),A", "ld (hl),a\ninc hl");
        addFakeInstruction("LDI (HL),B", "ld (hl),b\ninc hl");
        addFakeInstruction("LDI (HL),C", "ld (hl),c\ninc hl");
        addFakeInstruction("LDI (HL),D", "ld (hl),d\ninc hl");
        addFakeInstruction("LDI (HL),E", "ld (hl),e\ninc hl");
        addFakeInstruction("LDI (HL),H", "ld (hl),h\ninc hl");
        addFakeInstruction("LDI (HL),L", "ld (hl),l\ninc hl");
        addFakeInstruction("LDD (BC),A", "ld (bc),a\ndec bc");
        addFakeInstruction("LDD (DE),A", "ld (de),a\ndec de");
        addFakeInstruction("LDD (HL),A", "ld (hl),a\ndec hl");
        addFakeInstruction("LDD (HL),B", "ld (hl),b\ndec hl");
        addFakeInstruction("LDD (HL),C", "ld (hl),c\ndec hl");
        addFakeInstruction("LDD (HL),D", "ld (hl),d\ndec hl");
        addFakeInstruction("LDD (HL),E", "ld (hl),e\ndec hl");
        addFakeInstruction("LDD (HL),H", "ld (hl),h\ndec hl");
        addFakeInstruction("LDD (HL),L", "ld (hl),l\ndec hl");
        addFakeInstruction("SUB HL,BC", "or a\nsbc hl,bc");
        addFakeInstruction("SUB HL,DE", "or a\nsbc hl,de");
        addFakeInstruction("SUB HL,HL", "or a\nsbc hl,hl");
        addFakeInstruction("SUB HL,SP", "or a\nsbc hl,sp");
        addFakeInstruction("LD BC,(IX+o)", "ld c,(ix+o)\nld b,(ix+o+1)");
        addFakeInstruction("LD BC,(IY+o)", "ld c,(iy+o)\nld b,(iy+o+1)");
        addFakeInstruction("LD DE,(IX+o)", "ld e,(ix+o)\nld d,(ix+o+1)");
        addFakeInstruction("LD DE,(IY+o)", "ld e,(iy+o)\nld d,(iy+o+1)");
        addFakeInstruction("LD HL,(IX+o)", "ld l,(ix+o)\nld h,(ix+o+1)");
        addFakeInstruction("LD HL,(IY+o)", "ld l,(iy+o)\nld h,(iy+o+1)");
        addFakeInstruction("LD (IX+o),BC", "ld (ix+o),c\nld (ix+o+1),b");
        addFakeInstruction("LD (IX+o),DE", "ld (ix+o),e\nld (ix+o+1),d");
        addFakeInstruction("LD (IX+o),HL", "ld (ix+o),l\nld (ix+o+1),h");
        addFakeInstruction("LD (IY+o),BC", "ld (iy+o),c\nld (iy+o+1),b");
        addFakeInstruction("LD (IY+o),DE", "ld (iy+o),e\nld (iy+o+1),d");
        addFakeInstruction("LD (IY+o),HL", "ld (iy+o),l\nld (iy+o+1),h");
        addFakeInstruction("ldi BC,(IX+o)", "ld C,(IX+o)\ninc IX\nld b,(IX+o)\ninc IX");
        addFakeInstruction("ldi BC,(IY+o)", "ld C,(IY+o)\ninc IY\nld b,(IY+o)\ninc IY");
        addFakeInstruction("ldi DE,(IX+o)", "ld E,(IX+o)\ninc IX\nld d,(IX+o)\ninc IX");
        addFakeInstruction("ldi DE,(IY+o)", "ld E,(IY+o)\ninc IY\nld d,(IY+o)\ninc IY");
        addFakeInstruction("ldi HL,(IX+o)", "ld L,(IX+o)\ninc IX\nld h,(IX+o)\ninc IX");
        addFakeInstruction("ldi HL,(IY+o)", "ld L,(IY+o)\ninc IY\nld h,(IY+o)\ninc IY");
        addFakeInstruction("ldi (IX+o),BC", "ld (IX+o),C\ninc IX\nld (IX+o),B\ninc IX");
        addFakeInstruction("ldi (IX+o),DE", "ld (IX+o),E\ninc IX\nld (IX+o),D\ninc IX");
        addFakeInstruction("ldi (IX+o),HL", "ld (IX+o),L\ninc IX\nld (IX+o),H\ninc IX");
        addFakeInstruction("ldi (IY+o),BC", "ld (IY+o),C\ninc IY\nld (IY+o),B\ninc IY");
        addFakeInstruction("ldi (IY+o),DE", "ld (IY+o),E\ninc IY\nld (IY+o),D\ninc IY");
        addFakeInstruction("ldi (IY+o),HL", "ld (IY+o),L\ninc IY\nld (IY+o),H\ninc IY");
        addFakeInstruction("ldi A,(IX+o)", "ld A,(IX+o)\ninc IX");
        addFakeInstruction("ldi B,(IX+o)", "ld B,(IX+o)\ninc IX");
        addFakeInstruction("ldi C,(IX+o)", "ld C,(IX+o)\ninc IX");
        addFakeInstruction("ldi D,(IX+o)", "ld D,(IX+o)\ninc IX");
        addFakeInstruction("ldi E,(IX+o)", "ld E,(IX+o)\ninc IX");
        addFakeInstruction("ldi H,(IX+o)", "ld H,(IX+o)\ninc IX");
        addFakeInstruction("ldi L,(IX+o)", "ld L,(IX+o)\ninc IX");
        addFakeInstruction("ldi A,(IY+o)", "ld A,(IY+o)\ninc IY");
        addFakeInstruction("ldi B,(IY+o)", "ld B,(IY+o)\ninc IY");
        addFakeInstruction("ldi C,(IY+o)", "ld C,(IY+o)\ninc IY");
        addFakeInstruction("ldi D,(IY+o)", "ld D,(IY+o)\ninc IY");
        addFakeInstruction("ldi E,(IY+o)", "ld E,(IY+o)\ninc IY");
        addFakeInstruction("ldi H,(IY+o)", "ld H,(IY+o)\ninc IY");
        addFakeInstruction("ldi L,(IY+o)", "ld L,(IY+o)\ninc IY");
        addFakeInstruction("ldd A,(IX+o)", "ld A,(IX+o)\ndec IX");
        addFakeInstruction("ldd B,(IX+o)", "ld B,(IX+o)\ndec IX");
        addFakeInstruction("ldd C,(IX+o)", "ld C,(IX+o)\ndec IX");
        addFakeInstruction("ldd D,(IX+o)", "ld D,(IX+o)\ndec IX");
        addFakeInstruction("ldd E,(IX+o)", "ld E,(IX+o)\ndec IX");
        addFakeInstruction("ldd H,(IX+o)", "ld H,(IX+o)\ndec IX");
        addFakeInstruction("ldd L,(IX+o)", "ld L,(IX+o)\ndec IX");
        addFakeInstruction("ldd A,(IY+o)", "ld A,(IY+o)\ndec IY");
        addFakeInstruction("ldd B,(IY+o)", "ld B,(IY+o)\ndec IY");
        addFakeInstruction("ldd C,(IY+o)", "ld C,(IY+o)\ndec IY");
        addFakeInstruction("ldd D,(IY+o)", "ld D,(IY+o)\ndec IY");
        addFakeInstruction("ldd E,(IY+o)", "ld E,(IY+o)\ndec IY");
        addFakeInstruction("ldd H,(IY+o)", "ld H,(IY+o)\ndec IY");
        addFakeInstruction("ldd L,(IY+o)", "ld L,(IY+o)\ndec IY");
        addFakeInstruction("ldi (IX+o),A", "ld (IX+o),a\ninc IX");
        addFakeInstruction("ldi (IX+o),B", "ld (IX+o),b\ninc IX");
        addFakeInstruction("ldi (IX+o),C", "ld (IX+o),c\ninc IX");
        addFakeInstruction("ldi (IX+o),D", "ld (IX+o),d\ninc IX");
        addFakeInstruction("ldi (IX+o),E", "ld (IX+o),e\ninc IX");
        addFakeInstruction("ldi (IX+o),H", "ld (IX+o),h\ninc IX");
        addFakeInstruction("ldi (IX+o),L", "ld (IX+o),l\ninc IX");
        addFakeInstruction("ldi (IY+o),A", "ld (IY+o),a\ninc IY");
        addFakeInstruction("ldi (IY+o),B", "ld (IY+o),b\ninc IY");
        addFakeInstruction("ldi (IY+o),C", "ld (IY+o),c\ninc IY");
        addFakeInstruction("ldi (IY+o),D", "ld (IY+o),d\ninc IY");
        addFakeInstruction("ldi (IY+o),E", "ld (IY+o),e\ninc IY");
        addFakeInstruction("ldi (IY+o),H", "ld (IY+o),h\ninc IY");
        addFakeInstruction("ldi (IY+o),L", "ld (IY+o),l\ninc IY");
        addFakeInstruction("ldd (IX+o),A", "ld (IX+o),a\ndec IX");
        addFakeInstruction("ldd (IX+o),B", "ld (IX+o),b\ndec IX");
        addFakeInstruction("ldd (IX+o),C", "ld (IX+o),c\ndec IX");
        addFakeInstruction("ldd (IX+o),D", "ld (IX+o),d\ndec IX");
        addFakeInstruction("ldd (IX+o),E", "ld (IX+o),e\ndec IX");
        addFakeInstruction("ldd (IX+o),H", "ld (IX+o),h\ndec IX");
        addFakeInstruction("ldd (IX+o),L", "ld (IX+o),l\ndec IX");
        addFakeInstruction("ldd (IY+o),A", "ld (IY+o),a\ndec IY");
        addFakeInstruction("ldd (IY+o),B", "ld (IY+o),b\ndec IY");
        addFakeInstruction("ldd (IY+o),C", "ld (IY+o),c\ndec IY");
        addFakeInstruction("ldd (IY+o),D", "ld (IY+o),d\ndec IY");
        addFakeInstruction("ldd (IY+o),E", "ld (IY+o),e\ndec IY");
        addFakeInstruction("ldd (IY+o),H", "ld (IY+o),h\ndec IY");
        addFakeInstruction("ldd (IY+o),L", "ld (IY+o),l\ndec IY");
        addFakeInstruction("ldi (HL),nn", "ld (HL),nn\ninc HL");
        addFakeInstruction("ldi (IX+o),nn", "ld (IX+o),nn\ninc IX");
        addFakeInstruction("ldi (IY+o),nn", "ld (IY+o),nn\ninc IY");
        addFakeInstruction("ldd (HL),nn", "ld (HL),nn\ndec HL");
        addFakeInstruction("ldd (IX+o),nn", "ld (IX+o),nn\ndec IX");
        addFakeInstruction("ldd (IY+o),nn", "ld (IY+o),nn\ndec IY");

        // recognized escape sequences by sjasm:
        Tokenizer.stringEscapeSequences.put("\\", "\\");
        Tokenizer.stringEscapeSequences.put("?", "\u0063");
        Tokenizer.stringEscapeSequences.put("'", "'");
        Tokenizer.stringEscapeSequences.put("\"", "\"");
        Tokenizer.stringEscapeSequences.put("a", "\u0007");
        Tokenizer.stringEscapeSequences.put("b", "\u0008");
        Tokenizer.stringEscapeSequences.put("d", "\u0127");
        Tokenizer.stringEscapeSequences.put("e", "\u0027");
        Tokenizer.stringEscapeSequences.put("f", "\u0012");
        Tokenizer.stringEscapeSequences.put("n", "\n");
        Tokenizer.stringEscapeSequences.put("r", "\r");
        Tokenizer.stringEscapeSequences.put("t", "\t");
        Tokenizer.stringEscapeSequences.put("v", "\u0011");
        config.lineParser.applyEscapeSequencesToIncludeArguments = false;
    }
    
    
    private boolean addFakeInstruction(String in, String out)
    {
        String data[] = {in,"1","ff ff","2", "","","","", "","","","", "false"};
        CPUOpSpec fakeSpec = config.opSpecParser.parseOpSpecLine(data, config);
        if (fakeSpec == null) {
            config.error("cannot parse fake instruction " + in);
            return false;
        } 

        fakeSpec.fakeInstructionEquivalent = new ArrayList<>();
        for(String line:out.split("\n")) {
            fakeSpec.fakeInstructionEquivalent.add(Tokenizer.tokenize(line));
        }
        
        config.opParser.addOpSpec(fakeSpec);        
        return true;
    }
    

    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("struct")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("map")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmap")) return true;
        if (tokens.size() >= 1 && tokens.get(0).startsWith("#")) return true; 
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("field")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("incdir")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defpage")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("code")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) return true;
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("[")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(":=")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("module")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmodule")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("jr.")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("jp.")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("djnz.")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("define")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("xdefine")) return true;
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("assign")) return true;

        for(SjasmStruct s:structs) {
            if (tokens.get(0).equals(s.name)) return true;
        }
        return false;
    }
    
    
    private String getLastAbsoluteLabel(SourceStatement s) 
    {
        while(s != null) {
            // sjasm considers any label as an absolute label, even if it's associated with an equ,
            // so, no need to check if s.label.isLabel() (as in asMSX):
            if (s.label != null &&
                !s.label.originalName.startsWith(".") &&
                !Tokenizer.isInteger(s.label.originalName)) {
                return s.label.originalName;
            } else {
                s = s.source.getPreviousStatementTo(s, s.source.code);
            }
        }
        return null;        
    }
    

    @Override
    public String newSymbolName(String name, Expression value, SourceStatement previous) {
        if (name.equalsIgnoreCase("struct")
                || name.equalsIgnoreCase("ends")
                || name.equalsIgnoreCase("byte")
                || name.equalsIgnoreCase("defb")
                || name.equalsIgnoreCase("word")
                || name.equalsIgnoreCase("defw")
                || name.equalsIgnoreCase("dword")
                || name.equalsIgnoreCase("map")
                || name.equalsIgnoreCase("endmap")
                || name.equalsIgnoreCase("field")
                || name.equalsIgnoreCase("assert")
                || name.equalsIgnoreCase("incdir")
                || name.equalsIgnoreCase("output")
                || name.equalsIgnoreCase("defpage")
                || name.equalsIgnoreCase("code")
                || name.equalsIgnoreCase("align")
                || name.equalsIgnoreCase("module")
                || name.equalsIgnoreCase("endmodule")) {
            return null;
        }
        if (name.startsWith(".")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(previous);
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name;
            } else {
                return name;
            }
        } else if (Tokenizer.isInteger(name)) {
            // it'startStatement a reusable label:
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            reusableLabelCounts.put(name, count+1);
            name =  "_sjasm_reusable_" + name + "_" + count;
        }
        
        symbolPage.put(name, currentPage);

        return name;
    }

    
    @Override
    public String symbolName(String name, SourceStatement previous) {
        if (name.startsWith(".")) {
            String lastAbsoluteLabel = getLastAbsoluteLabel(previous);
            if (lastAbsoluteLabel != null) {
                return lastAbsoluteLabel + name;
            } else {
                return name;
            }
        } else if ((name.endsWith("f") || name.endsWith("F")) && Tokenizer.isInteger(name.substring(0, name.length()-1))) {
            // it'startStatement a reusable label:
            name = name.substring(0, name.length()-1);
            int count = 1;
            if (reusableLabelCounts.containsKey(name)) {
                count = reusableLabelCounts.get(name);
            }
            return "_sjasm_reusable_" + name + "_" + count;
        } else if ((name.endsWith("b") || name.endsWith("B")) && Tokenizer.isInteger(name.substring(0, name.length()-1))) {
            // it'startStatement a reusable label:
            name = name.substring(0, name.length()-1);
            int count = reusableLabelCounts.get(name);
            return "_sjasm_reusable_" + name + "_" + (count-1);
        } else {            
            return name;
        }
    }

    
    @Override
    public List<SourceStatement> parseLine(List<String> tokens, SourceLine sl,
            SourceStatement s, SourceStatement previous, SourceFile source, CodeBase code) {
        List<SourceStatement> l = new ArrayList<>();
        l.add(s);
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("struct")) {
            tokens.remove(0);
            struct = new SjasmStruct();
            struct.name = tokens.remove(0);
            struct.file = source;
            config.lineParser.pushLabelPrefix(struct.name + ".");
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("struc")) {
                // TODO(santi@): investigate what is this syntax, I found it in this file: https://github.com/GuillianSeed/MetalGear/blob/master/constants/structures.asm
                // But it does not seem to be documented, I think it might be an error that sjasm just happens to swalow
                tokens.remove(0);
            }
            struct.file = source;
            struct.start = s;
            s.type = SourceStatement.STATEMENT_CONSTANT;
            SourceConstant c = new SourceConstant(struct.name, struct.name, null, s, config);
            s.label = c;
            if (code.addSymbol(c.name, c) != 1) return null;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) {
            tokens.remove(0);
            if (struct.file == null) {
                config.error("ends outside of a struct at " + sl);
                return null;
            }
            if (struct.file != source) {
                config.error("struct split among multiple files is not supported at " + sl);
                return null;
            }
            
            // Transform the struct into equ definitions with local labels:
            int offset = 0;
            int start = source.getStatements().indexOf(struct.start) + 1;
            for (int i = start; i < source.getStatements().size(); i++) {
                SourceStatement s2 = source.getStatements().get(i);
                int offset_prev = offset;
                switch (s2.type) {
                    case SourceStatement.STATEMENT_NONE:
                        break;
                    case SourceStatement.STATEMENT_DATA_BYTES:
                    case SourceStatement.STATEMENT_DATA_WORDS:
                    case SourceStatement.STATEMENT_DATA_DOUBLE_WORDS:
                    {
                        int size = s2.sizeInBytes(code, true, true, true);
                        offset += size;
                        if (s2.label != null) {
                            struct.attributeNames.add(s2.label.name);
                        } else {
                            struct.attributeNames.add(null);
                        }
                        struct.attributeSizes.add(size);
                        break;
                    }
                    default:
                        config.error("Unsupported statement (type="+s2.type+") inside a struct definition at " + sl);
                        return null;
                }
                if (s2.label != null) {
                    s2.type = SourceStatement.STATEMENT_CONSTANT;
                    s2.label.exp = Expression.constantExpression(offset_prev, config);
                } else {
                    s2.type = SourceStatement.STATEMENT_NONE;
                }                
            }

            // Record the struct for later:
            struct.start.label.exp = Expression.constantExpression(offset, config);
            structs.add(struct);
            config.lineParser.keywordsHintingALabel.add(struct.name);
            config.lineParser.popLabelPrefix();
            struct = null;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) {
            tokens.remove(0);
            // just ignore
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("map")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            mapCounterStack.add(0, mapCounter);
            mapCounter = exp.evaluateToInteger(s, code, false);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmap")) {
            tokens.remove(0);
            mapCounter = mapCounterStack.remove(0);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if ((tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("field")) ||
            tokens.get(0).startsWith("#")) {
            if (tokens.get(0).startsWith("#") && tokens.get(0).length() > 1) {
                // this is a "#" plus a number attached together inside of a "map" group...
                if (config.warning_ambiguous) {
                    config.warn(tokens.get(0) + " is ambiguous syntax, please add a space between the # token and the field size to differentiate it from a hex constant in: " + sl);
                }
                tokens.set(0, tokens.get(0).substring(1));
            } else {
                tokens.remove(0);
            }
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            if (s.label == null) {
                config.error("Field expression does not have a label at " + sl);
                return null;
            }
            if (struct != null) {
                s.label.exp = exp;
            } else {
                s.label.exp = Expression.constantExpression(mapCounter, false, true, config);
            }
            s.type = SourceStatement.STATEMENT_CONSTANT;
            mapCounter += exp.evaluateToInteger(s, code, false);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            Integer value = exp.evaluateToInteger(s, code, false);
            if (value == null || value == Expression.FALSE) {
                config.error("Assertion failed at " + sl);
                return null;
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("incdir")) {
            tokens.remove(0);
            String folder = "";
            while(!tokens.isEmpty()) {
                if (Tokenizer.isSingleLineComment(tokens.get(0)) || 
                    Tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                folder += tokens.remove(0);
            }

            // Make sure we don't have a windows/Unix path separator problem:
            if (folder.contains("\\")) folder = folder.replace("\\", File.separator);
            
            File path = new File(config.lineParser.pathConcat(source.getPath(), folder));
            config.includeDirectories.add(path);
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) {
            // Just ignore ...
            while(!tokens.isEmpty()) {
                if (Tokenizer.isSingleLineComment(tokens.get(0)) || 
                    Tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                tokens.remove(0);
            }
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defpage")) {
            tokens.remove(0);
            Expression pageExp = config.expressionParser.parse(tokens, s, previous, code);
            Expression pageStartExp = null;
            Expression pageSizeExp = null;
            if (pageExp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                pageStartExp = config.expressionParser.parse(tokens, s, previous, code);
                if (pageStartExp == null) {
                    config.error("Cannot parse expression at " + sl);
                    return null;
                }
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                pageSizeExp = config.expressionParser.parse(tokens, s, previous, code);
                if (pageSizeExp == null) {
                    config.error("Cannot parse expression at " + sl);
                    return null;
                }
            }
            int pageNumber = pageExp.evaluateToInteger(s, code, false);
            CodePage page = new CodePage(s, pageStartExp, pageSizeExp);
            pages.put(pageNumber, page);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("code")) {
            tokens.remove(0);
            Expression addressExp = null;
            if (!tokens.isEmpty() && tokens.get(0).equals("?")) {
                config.error("Unsupported form of code keyword at " + sl);
                return null;
            }                    
            if (!tokens.isEmpty() && tokens.get(0).equals("@")) {
                tokens.remove(0);
                addressExp = config.expressionParser.parse(tokens, s, previous, code);
                if (addressExp == null) {
                    config.error("Cannot parse expression at " + sl);
                    return null;
                }
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) tokens.remove(0);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals("#")) {
                config.error("Unsupported form of code keyword at " + sl);
                return null;
            }
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("page")) {
                tokens.remove(0);
                Expression pageExp = config.expressionParser.parse(tokens, s, previous, code);
                int page = pageExp.evaluateToInteger(s, code, false);
                currentPage = page;
            }            
            codeBlocks.add(new CodeBlock(s, currentPage, addressExp));
            
            // ignore (but still add the statement, so we know where the codeblock starts)
            s.type = SourceStatement.STATEMENT_NONE;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) {
            tokens.remove(0);
            Expression pageExp = null;
            pageExp = config.expressionParser.parse(tokens, s, previous, code);
            if (pageExp == null) {
                config.error("Cannot parse expression at " + sl);
                return null;
            }
            int page = pageExp.evaluateToInteger(s, code, false);
            currentPage = page;
            Expression addressExp = null;
            
            codeBlocks.add(new CodeBlock(s, currentPage, addressExp));            
            
            // parse it as an "org"
            s.type = SourceStatement.STATEMENT_NONE;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }        
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("[")) {
            tokens.remove(0);
            Expression numberExp = config.expressionParser.parse(tokens, s, previous, code);
            if (tokens.isEmpty() || !tokens.get(0).equals("]")) {
                config.error("Cannot parse line at " + sl);
                return null;
            }
            tokens.remove(0);
            int number = numberExp.evaluateToInteger(s, code, false);
            l.clear();
            for(int i = 0;i<number;i++) {
                List<String> tokensCopy = new ArrayList<>();
                tokensCopy.addAll(tokens);
                // we need to parse it every time, to create multiple different copies of the statements:
                config.expressionParser.sjasmConterVariables.add(i);
                List<SourceStatement> l2 = config.lineParser.parse(tokensCopy, sl, source, previous, code, config);
                config.expressionParser.sjasmConterVariables.remove(config.expressionParser.sjasmConterVariables.size()-1);
                if (l2 == null) {
                    config.error("Cannot parse line at " + sl);
                    return null;
                }

                l.addAll(l2);
            }
            return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(":=")) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode at " + sl);
                return null;
            }
            
            tokens.remove(0);
            s.label.resolveEagerly = true;
            if (!config.lineParser.parseEqu(tokens, sl, s, previous, source, code)) return null;
            s.label.clearCache();
            Integer value = s.label.exp.evaluateToInteger(s, code, false);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return null;
            }
            s.label.exp = Expression.constantExpression(value, config);
            
            // these variables should not be part of the source code:
            l.clear();
            return l;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) {
            tokens.remove(0);
            if (!config.lineParser.parseData(tokens, "db", sl, s, previous, source, code)) return null;
            // insert a "0" at the end of each string:
            List<Expression> newData = new ArrayList<>();
            for(Expression exp:s.data) {
                newData.add(exp);
                newData.add(Expression.constantExpression(0, config));
            }
            s.data = newData;
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return null;
            }
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            // ds (((($-1)/exp)+1)*exp-$)
            s.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                        Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                          Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                              Expression.operatorExpression(Expression.EXPRESSION_DIV, 
                                Expression.parenthesisExpression(
                                  Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                                      Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), 
                                      Expression.constantExpression(1, config), config), "(", config),
                                  exp, config), 
                              Expression.constantExpression(1, config), config), "(", config), 
                          exp, config),
                        Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config), config);
            s.space_value = Expression.constantExpression(0, config);
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }   
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("module")) {
            tokens.remove(0);
            String moduleName = tokens.remove(0);
            
            modules.add(0, moduleName);
            config.lineParser.pushLabelPrefix(moduleName + ".");
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmodule")) {
            tokens.remove(0);
            String moduleName = null;
            if (tokens.size() >= 1) {
                moduleName = tokens.remove(0);
            }
            
            if (moduleName == null) {
                modules.remove(0);
                config.lineParser.popLabelPrefix();
            } else {
                boolean found = false;
                while(!modules.isEmpty()) {
                    if (modules.get(0).equals(moduleName)) {
                        modules.remove(0);
                        config.lineParser.popLabelPrefix();
                        found = true;
                        break;
                    } else {
                        modules.remove(0);
                        config.lineParser.popLabelPrefix();
                    }
                }
                if (!found) {
                    config.error("Cannot close unexistent module at " + sl);
                    return null;
                }
            }
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("jr.")) {
            // store in a list:
            enhancedJrList.add(s);

            // parse as "jp":
            tokens.remove(0);
            if (config.lineParser.parseCPUOp(tokens, "jp", sl, l, previous, source, code)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("jp.")) {
            // store in a list:
            enhancedJrList.add(s);

            // parse as "jp":
            tokens.remove(0);
            if (config.lineParser.parseCPUOp(tokens, "jp", sl, l, previous, source, code)) return l;
            return null;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("djnz.")) {
            // store in a list:
            enhancedDjnzList.add(s);

            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse argument at " + sl);
                return null;                
            }
            
            // parse as "dec b; jp nz,label":
            {
                SourceStatement auxiliaryS = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                List<Expression> auxiliaryArguments = new ArrayList<>();
                auxiliaryArguments.add(Expression.symbolExpression("b", auxiliaryS, code, config));
                List<CPUOp> op_l = config.opParser.parseOp("dec", auxiliaryArguments, s, previous, code);
                if (op_l == null || op_l.size() != 1) return null;
                auxiliaryS.op = op_l.get(0);
                l.add(0, auxiliaryS);
            }
            {
                List<Expression> auxiliaryArguments = new ArrayList<>();
                auxiliaryArguments.add(Expression.symbolExpression("nz", s, code, config));
                auxiliaryArguments.add(exp);
                List<CPUOp> op_l = config.opParser.parseOp("jp", auxiliaryArguments, s, previous, code);
                if (op_l == null || op_l.size() != 1) return null;
                s.type = SourceStatement.STATEMENT_CPUOP;
                s.op = op_l.get(0);
            }
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }
        if (tokens.size() >= 2 && 
                (tokens.get(0).equalsIgnoreCase("define") ||
                 tokens.get(0).equalsIgnoreCase("xdefine") ||
                 tokens.get(0).equalsIgnoreCase("assign"))) {
            String keyword = tokens.remove(0);

            // read variable name:
            String token = tokens.remove(0);
            if (!config.lineParser.caseSensitiveSymbols) token = token.toLowerCase();
            String symbolName = config.lineParser.newSymbolName(token, null, previous);
            if (symbolName == null) {
                config.error("Problem defining symbol " + config.lineParser.getLabelPrefix() + token + " in " + sl);
                return null;
            }
            
            // optionally read the expression:
            Expression exp = null;
            if (!tokens.isEmpty() && !Tokenizer.isSingleLineComment(tokens.get(0))) {                    
                exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("parseEqu: Cannot parse line " + sl);
                    return null;
                }
                // remove unnecessary parenthesis:
                while(exp.type == Expression.EXPRESSION_PARENTHESIS) {
                    exp = exp.args.get(0);
                }
                
                if (keyword.equalsIgnoreCase("xdefine")) {
                    // resolve symbols internally:
                    exp = exp.resolveEagerSymbols(code);
                    
                } else if (keyword.equalsIgnoreCase("assign")) {
                    Integer v = exp.evaluateToInteger(s, code, false);
                    if (v == null) {
                        config.error("Could not evaulate " + exp + " in " + sl);
                        return null;
                    }
                    exp = Expression.constantExpression(v, config);
                }
            } else {
                exp = Expression.constantExpression(0, config);
            }

            // parse as a :=:            
            SourceConstant c = new SourceConstant(symbolName, token, exp, s, config);
            s.label = c;
            int res = code.addSymbol(c.name, c);
            if (res == -1) return null;
            if (res == 0) s.redefinedLabel = true;
            s.label.resolveEagerly = true;
            
            // these variables should not be part of the source code:
            l.clear();
            
            if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
            return null;
        }        

        
        // struct definitions:
        for(SjasmStruct st:structs) {
            if (tokens.get(0).equals(st.name)) {
                tokens.remove(0);
                // it is a struct definition:
                boolean done = false;
                List<Expression> data = new ArrayList<>();
                while (!done) {
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp == null) {
                        config.error("Cannot parse line " + sl);
                        return null;
                    } else {
                        data.add(exp);
                    }
                    if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                        tokens.remove(0);
                    } else {
                        done = true;
                    }
                }
                if (data.size() != st.attributeSizes.size()) {
                    config.error("Struct instantiation has the wrong number of fields ("+data.size()+" vs the expected "+st.attributeSizes.size()+") in " + sl);
                    return null;                    
                }
                l.clear();
                
                for(int i = 0;i<data.size();i++) {
                    SourceStatement s2;
                    switch(st.attributeSizes.get(i)) {
                        case 1:
                            s2 = new SourceStatement(SourceStatement.STATEMENT_DATA_BYTES, sl, source, config);
                            break;
                        case 2:
                            s2 = new SourceStatement(SourceStatement.STATEMENT_DATA_WORDS, sl, source, config);
                            break;
                        case 4:
                            s2 = new SourceStatement(SourceStatement.STATEMENT_DATA_DOUBLE_WORDS, sl, source, config);
                            break;
                        default:
                            config.error("Field " + st.attributeNames.get(i) + " of struct " + st.name + " has an unsupported size in: " + sl);
                            return null;
                    }
                    if (i == 0) s2.label = s.label;
                    s2.data = new ArrayList<>();
                    s2.data.add(data.get(i));
                    l.add(s2);
                }
                if (config.lineParser.parseRestofTheLine(tokens, sl, s, source)) return l;
                break;
            }
        }
        
        return null;
    }


    @Override
    public boolean parseFakeCPUOps(List<String> tokens, SourceLine sl, List<SourceStatement> l, SourceStatement previous, SourceFile source, CodeBase code) 
    {
        // This function only adds the additional instructions beyond the first one. So, it will leave in "tokens", the set of
        // tokens necessary for MDL's regular parser to still parse the first op
        SourceStatement s = l.get(0);
        
        if (tokens.size()>=4 && 
            (tokens.get(0).equalsIgnoreCase("push") || tokens.get(0).equalsIgnoreCase("pop"))) {
            // instructions of the style: push bc,de,hl
            List<String> regpairs = new ArrayList<>();
            boolean process = true;
            int idx = 1;
            while(true) {
                if (tokens.size()<=idx) {
                    process = false;
                    break;
                }
                String regpair = tokens.get(idx);
                if (!code.isRegisterPair(regpair)) {
                    process = false;
                    break;
                }
                regpairs.add(regpair);
                idx++;
                if (tokens.size()<=idx) break;
                if (Tokenizer.isSingleLineComment(tokens.get(idx))) break;
                if (!tokens.get(idx).equals(",")) {
                    process = false;
                    break;
                }
                idx++;
            }
            if (process && regpairs.size()>1) {
                int toremove = (regpairs.size()-1)*2;
                for(int i = 0;i<toremove;i++) tokens.remove(2);
                for(int i = 1;i<regpairs.size();i++) {
                    SourceStatement auxiliaryS = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                    List<Expression> auxiliaryArguments = new ArrayList<>();
                    auxiliaryArguments.add(Expression.symbolExpression(regpairs.get(i), auxiliaryS, code, config));
                    List<CPUOp> op_l = config.opParser.parseOp(tokens.get(0), auxiliaryArguments, s, previous, code);
                    if (op_l == null || op_l.size() != 1) return false;
                    auxiliaryS.op = op_l.get(0);
                    l.add(auxiliaryS);                    
                }
                return true;
            }
        }
        
        // see if there is a pre/post increment of a registerpair:
        for (int i = 0; i < tokens.size(); i++) {
            if (i < tokens.size() - 1
                    && (tokens.get(i).equals("++") || tokens.get(i).equals("--"))
                    && code.isRegisterPair(tokens.get(i + 1))) {
                // pre increment/decrement:
                SourceStatement auxiliaryS = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                List<Expression> auxiliaryArguments = new ArrayList<>();
                auxiliaryArguments.add(Expression.symbolExpression(tokens.get(i + 1), auxiliaryS, code, config));
                List<CPUOp> op_l = config.opParser.parseOp(tokens.get(i).equals("++") ? "inc" : "dec", auxiliaryArguments, s, previous, code);
                if (op_l == null || op_l.size() != 1) return false;
                auxiliaryS.op = op_l.get(0);
                l.add(0, auxiliaryS);
                tokens.remove(i);
                if (config.warning_unofficialOps) {
                    config.warn("Unofficial op (fake instruction) used in " + sl);
                }
                break;
            }
            if (i > 0
                    && (tokens.get(i).equals("++") || tokens.get(i).equals("--"))
                    && code.isRegisterPair(tokens.get(i - 1))) {
                // post increment/decrement:
                SourceStatement auxiliaryS = new SourceStatement(SourceStatement.STATEMENT_CPUOP, sl, source, config);
                List<Expression> auxiliaryArguments = new ArrayList<>();
                auxiliaryArguments.add(Expression.symbolExpression(tokens.get(i - 1), auxiliaryS, code, config));
                List<CPUOp> op_l = config.opParser.parseOp(tokens.get(i).equals("++") ? "inc" : "dec", auxiliaryArguments, s, previous, code);
                if (op_l == null || op_l.size() != 1) return false;
                auxiliaryS.op = op_l.get(0);
                l.add(auxiliaryS);
                tokens.remove(i);
                if (config.warning_unofficialOps) {
                    config.warn("Unofficial op (fake instruction) used in " + sl);
                }
                break;
            }
        }

        return true;
    }


    @Override
    public Integer evaluateExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code, boolean silent)
    {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            Integer value = args.get(0).evaluateToInteger(s, code, silent);
            if (value == null) return null;
            return (value >> 8)&0xff;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            Integer value = args.get(0).evaluateToInteger(s, code, silent);
            if (value == null) return null;
            return value&0xff;
        }
        if (functionName.equalsIgnoreCase(":") && args.size() == 1) {
            if (args.get(0).type != Expression.EXPRESSION_SYMBOL) {
                config.error("':' operator used on a non-symbol expression at " + s.sl);
                return null;
            }
            Integer page = symbolPage.get(args.get(0).symbolName);
            if (page == null) {
                config.error("Unknown page of symbol "+args.get(0).symbolName+" at " + s.sl);
                return null;
            }
            return page;
        }
        return null;
    }

    
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, SourceStatement s, CodeBase code) {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, false, true, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, false, true, config), config);
        }

        return null;
    }
    
    
    @Override
    public MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, SourceStatement macroCall, CodeBase code)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        MacroExpansion me = new MacroExpansion(macro, macroCall, lines2);
        
        if (macro.name.equals("repeat")) {
            if (args.isEmpty()) return null;
            Integer iterations_tmp = args.get(0).evaluateToInteger(macroCall, code, false);
            if (iterations_tmp == null) {
                config.error("Could not evaluate nmber of iterations when expanding macro in " + macroCall.sl);
                return null;
            }
            int iterations = iterations_tmp.intValue();
//            String scope;
//            if (macroCall.label != null) {
//                scope = macroCall.label.name;
//            } else {
//                scope = config.preProcessor.nextMacroExpansionContextName();
//            }
            for(int i = 0;i<iterations;i++) {
                String variable = "@#";
                List<SourceLine> linesTmp = new ArrayList<>();
                for(SourceLine sl:macro.lines) {
                    // we create new instances, as we will modify them:
                    linesTmp.add(new SourceLine(sl.line, sl.source, sl.lineNumber));
                }
                // macro.scopeMacroExpansionLines(scope+"."+i, linesTmp, code, config);
                for(SourceLine sl:linesTmp) {
                    String line2 = sl.line;
                    StringTokenizer st = new StringTokenizer(line2, " \t");
                    if (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        if (token.equalsIgnoreCase("repeat")) {
                            variable = "@" + variable;
                        } else if (token.equalsIgnoreCase("endrepeat")) {
                            variable = variable.substring(1);
                        }
                    }
                    line2 = line2.replace(variable, i + "");
                    lines2.add(new SourceLine(line2, sl.source, sl.lineNumber));
                }
            }
            return me;
        } else {
            return null;
        }
    }
    

    @Override
    public boolean performAnyFinalActions(CodeBase code)
    {
        if (reusableLabelCounts.size() > 0 && config.warning_ambiguous) {
            config.warn("Use of sjasm reusable labels, which are conductive to human error.");
        }
                
        // if there are no blocks defined, we are done:
        if (!codeBlocks.isEmpty()) {
                
            // Reorganize all the "code" blocks into the different pages:
            CodeBlock initialBlock = new CodeBlock(null, -1, null);
            CodeBlock currentBlock = initialBlock;

            {
                // get the very first statement, and iterate over all the rest:
                SourceStatement s = code.getMain().getStatements().get(0);
                while(s != null) {
                    if (s.type == SourceStatement.STATEMENT_INCLUDE) {
                        s = s.include.getStatements().get(0);
                    } else {
                        // See if a new block starts:
                        CodeBlock block = blockStartingAt(s);
                        if (block == null) {
                            currentBlock.statements.add(s);
                        } else {
                           currentBlock = block;
                           currentBlock.statements.add(s);
                        }
                        s = s.source.getNextStatementTo(s, code);
                    }
                }
              }

            if (initialBlock.size(code) > 0) {
                config.error("sjasm initial block has non empty size!");
                return false;
            }
            
            
            // Resolve enhanced jr/jp, only within-block:
            resolveEnhancedJumps(code);

            // Assign blocks to pages, first those that have a defined address, then the rest:
            // Start with those that have a defined address:
            List<CodeBlock> blocksToAssign = new ArrayList<>();
            for(CodeBlock b:codeBlocks) {
                Integer address = null;
                if (b.address != null) address = b.address.evaluateToInteger(b.startStatement, code, true);
                if (address == null) {
                    blocksToAssign.add(b);
                } else {
                    CodePage page = pages.get(b.page);
                    if (!page.addBlock(b, code)) {
                        config.error("Could not add block of size " + b.size(code) + " to page " + page + "!");
                        return false;
                    }
                }
            }

            // Sort the rest of codeblocks by size, and assign them to pages where they fit:
            Collections.sort(blocksToAssign, new Comparator<CodeBlock>() {
                @Override
                public int compare(CodeBlock b1, CodeBlock b2) {
                    return -Integer.compare(b1.size(code), b2.size(code));
                }
            });

            for(CodeBlock b:blocksToAssign) {
                CodePage page = pages.get(b.page);
                if (!page.addBlock(b, code)) {
                    config.error("Could not add block of size " + b.size(code) + " to page " + page + "!");
                    return false;
                }
            }

            // Create a new source file, and add all the code blocks:
            SourceFile reconstructedFile = new SourceFile(code.getMain().fileName, null, null, code, config);
            code.getSourceFiles().clear();
            code.addSourceFile(reconstructedFile);
            code.setMain(reconstructedFile);

            // start by adding initialBlock:
            for(SourceStatement s:initialBlock.statements) {
                s.source = reconstructedFile;
                reconstructedFile.addStatement(s);
            }

            // add the rest of blocks, inserting appropriate spacing in between:
            List<Integer> pageIndexes = new ArrayList<>();
            pageIndexes.addAll(pages.keySet());
            Collections.sort(pageIndexes);

            for(int idx:pageIndexes) {
                CodePage page = pages.get(idx);
                SourceStatement org = new SourceStatement(SourceStatement.STATEMENT_ORG, new SourceLine("", reconstructedFile, reconstructedFile.getStatements().size()+1), reconstructedFile, config);
                org.org = page.start;
                reconstructedFile.addStatement(org);
                int pageStart = page.start.evaluateToInteger(page.s, code, true);
                int pageSize = page.size.evaluateToInteger(page.s, code, true);
                int currentAddress = pageStart;
                for(CodeBlock block:page.blocks) {
                    if (block.actualAddress > currentAddress) {
                        // insert space:
                        SourceStatement space = new SourceStatement(SourceStatement.STATEMENT_DEFINE_SPACE, new SourceLine("", reconstructedFile, reconstructedFile.getStatements().size()+1), reconstructedFile, config);
                        space.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                        Expression.constantExpression(block.actualAddress, config),
                                        Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, space, code, config),
                                        config);
                        space.space_value = Expression.constantExpression(0, config);
                        reconstructedFile.addStatement(space);
                        config.debug("inserting space (end of block) of " + (block.actualAddress-currentAddress));
                    }
                    for(SourceStatement s:block.statements) {
                        s.source = reconstructedFile;
                        reconstructedFile.addStatement(s);
                    }
                    code.resetAddresses();
                    currentAddress = block.actualAddress + block.size(code);
                }
                if (currentAddress < pageStart + pageSize) {
                    // insert space:
                    SourceStatement space = new SourceStatement(SourceStatement.STATEMENT_DEFINE_SPACE, new SourceLine("", reconstructedFile, reconstructedFile.getStatements().size()+1), reconstructedFile, config);
                    space.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                    Expression.constantExpression(pageStart + pageSize, config),
                                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, space, code, config),
                                    config);
                    space.space_value = Expression.constantExpression(0, config);
                    reconstructedFile.addStatement(space);
                    config.debug("inserting space (end of page) of " + ((pageStart + pageSize) - currentAddress) + " from " + currentAddress + " to " + (pageStart + pageSize));
                }
                config.debug("page " + idx + " from " + pageStart + " to " + (pageStart+pageSize));
            }

            code.resetAddresses();
        } else {
            resolveEnhancedJumps(code);
        }
        return true;
    }
        
    
    public CodeBlock findCodeBlock(SourceStatement s)
    {
        for(CodeBlock cb:codeBlocks) {
            if (cb.statements.contains(s)) return cb;
        }
        
        return null;
    }
    

    public void resolveEnhancedJumps(CodeBase code)
    {
        // resolve enhanced jumps:
        for(SourceStatement s:enhancedJrList) {
            CodeBlock b1 = findCodeBlock(s);
            CodeBlock b2 = null;
            if (s.op != null && !s.op.args.isEmpty()) {
                Expression label = s.op.args.get(s.op.args.size()-1);
                if (label.type == Expression.EXPRESSION_SYMBOL) {
                    SourceConstant c = code.getSymbol(label.symbolName);
                    if (c != null) {
                        b2 = findCodeBlock(c.definingStatement);
                    } else {
                    }
                } else {
                }
                // only consider jumps within the same block:
                if (b1 == b2) {
                    Integer address = s.getAddress(code);
                    Integer targetAddress = label.evaluateToInteger(null, code, true);
                    if (address != null && targetAddress != null) {
                        int diff = targetAddress - address;
                        if (diff >= -126 && diff <= 130) {
                            // change to jr!:
                            List<CPUOp> l = config.opParser.parseOp("jr", s.op.args, s, 
                                    s.source.getPreviousStatementTo(s, code), code);
                            if (l != null && l.size() == 1) {
                                s.op = l.get(0);
                                code.resetAddresses();
                            }
                        }
                    }
                }
            }
        }
        for(SourceStatement s:enhancedDjnzList) {
            CodeBlock b1 = findCodeBlock(s);
            CodeBlock b2 = null;
            if (s.op != null && !s.op.args.isEmpty()) {
                SourceStatement previous = s.source.getPreviousStatementTo(s, code);
                if (previous.source == s.source && 
                        previous.op != null && 
                        previous.op.spec.getName().equalsIgnoreCase("dec")) {
                    Expression label = s.op.args.get(s.op.args.size()-1);
                    if (label.type == Expression.EXPRESSION_SYMBOL) {
                        SourceConstant c = code.getSymbol(label.symbolName);
                        if (c != null) {
                            b2 = findCodeBlock(c.definingStatement);
                        }
                    }
                    // only consider jumps within the same block:
                    if (b1 == b2) {                    
                        Integer address = s.getAddress(code);
                        Integer targetAddress = label.evaluateToInteger(null, code, true);
                        if (address != null && targetAddress != null) {
                            int diff = targetAddress - address;
                            if (diff >= -126 && diff <= 130) {
                                // change to djnz!:
                                List<Expression> args = new ArrayList<>();
                                args.add(label);
                                List<CPUOp> l = config.opParser.parseOp("djnz", args, s, previous, code);
                                if (l != null && l.size() == 1) {
                                    previous.source.getStatements().remove(previous);
                                    s.op = l.get(0);
                                    code.resetAddresses();
                                }
                            }
                        }
                    }
                }
            }
        }
        
        code.resetAddresses();
    }
    
    
    CodeBlock blockStartingAt(SourceStatement s)
    {
        for(CodeBlock b:codeBlocks) {
            if (b.startStatement == s) return b;
        }
        return null;
    }
    
    
    // Sjasm is an eager-evaluated assembler, which clashes with the lazy 
    // evaluation done by MDL. So, in order to capture the "rotate" syntax used
    // in sjasm when used together with variable number of argument macros in
    // order to have a similar functionality as Glass' IRP macro, we cover 
    // here a basic common case (if needed, more cases will be covered in the 
    // future, but this syntax is very problematic given the way macros are
    // resolved in MDL...):    
    public List<SourceLine> expandVariableNumberOfArgsMacro(List<Expression> args, SourceStatement macroCall, SourceMacro macro, CodeBase code, MDLConfig config)
    {
        List<SourceLine> lines2 = new ArrayList<>();
        
        List<String> argNames = new ArrayList<>();
        for(int i = 0;i<args.size()+1;i++) {
            argNames.add("@" + i);
        }
        // first argument is the number of args:
        args.add(0, Expression.constantExpression(args.size(), config));
                
        List<SourceLine> repeatLines = null;
        List<SourceLine> repeatLinesToExecute = null;
        
        for(SourceLine sl:macro.lines) {
            String line2 = sl.line;
            List<String> tokens = Tokenizer.tokenizeIncludingBlanks(line2);
            line2 = "";

            boolean allEmptySoFar = true;
            for(String token:tokens) {
                if (allEmptySoFar) {
                    if (token.equalsIgnoreCase("repeat")) {
                        if (repeatLines != null) {
                            config.error("nested repeats in variable argument macros not yet supported in " + sl);
                            return null;
                        }
                        repeatLines = new ArrayList<>();
                    } else if (token.equalsIgnoreCase("endrepeat")) {
                        if (repeatLines == null) {
                            config.error("mismatched endrepeat in " + sl);
                            return null;
                        }
                        repeatLinesToExecute = repeatLines;
                        repeatLines = null;
                    }
                }
                
                if (!token.trim().equals("")) allEmptySoFar = false;
                
                if (repeatLines == null || repeatLines.isEmpty()) {
                    String newToken = token;
                    for(int i = 0;i<argNames.size();i++) {
                        if (token.equals(argNames.get(i))) {
                            // we wrap it spaces, to prevent funny interaction of tokens, e.g., two "-" in a row forming a "--":
                            newToken = " " + args.get(i).toString() + " ";
                        }
                    }
                    line2 += newToken;
                } else {
                    line2 += token;
                }
            }
            
            if (repeatLinesToExecute != null) {
                SourceLine repeatStatement = repeatLinesToExecute.remove(0);
                List<String> tokens2 = Tokenizer.tokenize(repeatStatement.line);
                tokens2.remove(0);  // skip "repeat"
                Expression exp = config.expressionParser.parse(tokens2, macroCall, macroCall.source.getPreviousStatementTo(macroCall, code), code);
                int nIterations = exp.evaluateToInteger(macroCall, code, false);
                for(int i = 0;i<nIterations;i++) {
                    for(SourceLine sl3:repeatLinesToExecute) {
                        List<String> tokens3 = Tokenizer.tokenizeIncludingBlanks(sl3.line);
                        String line3 = "";
                        for(String token:tokens3) {
                            String newToken = token;
                            for(int j = 0;j<argNames.size();j++) {
                                if (token.equals(argNames.get(j))) {
                                    newToken = args.get(j).toString();
                                }
                            }
                            line3 += newToken;
                        }
                        
                        if (line3.trim().toLowerCase().startsWith("rotate ")) {
                            // execute a rotate:
                            List<String> tokensRotate = Tokenizer.tokenize(line3);
                            tokensRotate.remove(0); // skip "rotate"
                            Expression expRotate = config.expressionParser.parse(tokensRotate, macroCall, macroCall.source.getPreviousStatementTo(macroCall, code), code);
                            int nRotations = expRotate.evaluateToInteger(macroCall, code, false);
                            while(nRotations>0) {
                                Expression argExp = args.remove(1);
                                args.add(argExp);
                                nRotations--;
                            }
                            while(nRotations<0) {
                                Expression argExp = args.remove(args.size()-1);
                                args.add(0, argExp);
                                nRotations++;
                            }
                        } else {                            
                            lines2.add(new SourceLine(line3, sl3.source, sl3.lineNumber, macroCall));
                        }
                    }
                }
                repeatLinesToExecute = null;
            } else if (repeatLines == null) {
                lines2.add(new SourceLine(line2, sl.source, sl.lineNumber, macroCall));
            } else {
                repeatLines.add(new SourceLine(line2, sl.source, sl.lineNumber, macroCall));
            }
        }   

        return lines2;
    }
}

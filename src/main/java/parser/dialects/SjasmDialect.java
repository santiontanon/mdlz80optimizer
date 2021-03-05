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
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.StringTokenizer;
import parser.LineParser;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;
import parser.TextMacro;
import workers.reorgopt.CodeBlock;

/**
 *
 * @author santi
 */
public class SjasmDialect extends SjasmDerivativeDialect implements Dialect 
{    
    public class SJasmCodeBlock extends CodeBlock {
        List<Integer> candidatePages = null;
        Expression address;
        Expression alignment;
        int actualAddress = -1;
        
        public SJasmCodeBlock(CodeStatement a_s, List<Integer> a_candidatePages, Expression a_address, Expression a_alignment)
        {
            super(null, CodeBlock.TYPE_UNKNOWN, a_s);
            startStatement = a_s;
            candidatePages = new ArrayList<>();
            if (a_candidatePages != null) {
                candidatePages.addAll(a_candidatePages);
            }
            address = a_address;
            alignment = a_alignment;
        }
        
        public Integer size(CodeBase code)
        {
            int size = 0;
            for(CodeStatement s:statements) {
                Integer sSize = s.sizeInBytes(code, false, true, false);
                if (sSize == null) {
                    config.error("Cannot evaluate the size of a statement in " + s.sl);
                    return null;
                }
                size += sSize;
            }
            return size;
        }
    }


    public class CodePage {
        public CodeStatement s;
        public Expression start;
        public Expression size;
        public List<SJasmCodeBlock> blocks = new ArrayList<>();
        
        public CodePage(CodeStatement a_s, Expression a_start, Expression a_size)
        {
            s = a_s;
            start = a_start;
            size = a_size;
        }
        
        
        public boolean addBlock(SJasmCodeBlock block, CodeBase code, MDLConfig config)
        {
            int spot = -1;
            int alignment = 1;
            if (block.alignment != null) {
                Integer tmp = block.alignment.evaluateToInteger(null, code, true);
                if (tmp == null) {
                    config.error("Could not evaluate the alignment expression of a block: " + block.alignment);
                    return false;
                }
                alignment = tmp;
            }
            if (block.address != null) {
                // check if it fits:
                int blockAddress = block.address.evaluateToInteger(block.startStatement, code, true);
                for(SJasmCodeBlock b2:blocks) {
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
                Integer pageStart = start.evaluateToInteger(s, code, true);
                if (pageStart == null) {
                    config.error("Cannot evaluate expression " + start + " while finding a spot for a block");
                    return false;                    
                }
                int blockAddress = pageStart;
                int alignedAddress = blockAddress + (alignment - (blockAddress%alignment))%alignment;
                Integer blockSize = block.size(code);
                
                if (blockSize == null) {
                    config.error("Cannot assess block size!");
                    return false;
                }
                
                for(SJasmCodeBlock b2:blocks) {                    
                    if (b2.actualAddress-alignedAddress >= blockSize) {
                        // found a spot!
                        spot = blocks.indexOf(b2);
                        break;
                    }
                    blockAddress = b2.actualAddress+b2.size(code);
                    alignedAddress = blockAddress + (alignment - (blockAddress%alignment))%alignment;
                }
                if (spot == -1) {
                    // add at the end:
                    // Make sure the address is properly aligned:
                    alignedAddress = blockAddress + (alignment - (blockAddress%alignment))%alignment;
                    if (size != null) {
                        Integer evaluatedSize = size.evaluateToInteger(s, code, false);
                        if (evaluatedSize == null) {
                            config.error("Cannot evaluate expression " + evaluatedSize + " while finding a spot for a block");
                            return false;
                        }
                        int spaceLeft = (pageStart + evaluatedSize) - alignedAddress;
                        if (spaceLeft >= blockSize) {
                            block.actualAddress = alignedAddress;
                            blocks.add(block);
                        } else {
                            return false;
                        }
                    } else {
                        block.actualAddress = alignedAddress;
                        blocks.add(block);                        
                    }
                } else {
                    block.actualAddress = alignedAddress;
                    blocks.add(spot, block);
                }
                return true;
            }
        }
    }
    
    
    public class SJasmOutput {
        String ID;
        String fileName;
        CodeStatement startStatement;
        HashMap<Integer,CodePage> pages = new HashMap<>();
        List<SJasmCodeBlock> codeBlocks = new ArrayList<>();
        SJasmCodeBlock defaultBlock;
        
        SourceFile reconstructedFile = null;
        
        public SJasmOutput(String a_ID, String a_fileName, CodeStatement a_startStatement) {
            ID = a_ID;
            fileName = a_fileName;
            startStatement = a_startStatement;
            
            List<Integer> currentPages = new ArrayList<>();
            currentPages.add(0);
            defaultBlock = new SJasmCodeBlock(a_startStatement, currentPages, null, null);            
        }
    }
    
    
    int mapCounter = 0;
    List<Integer> mapCounterStack = new ArrayList<>();
        
//    List<SJasmCodeBlock> codeBlocks = new ArrayList<>();
//    List<SJasmCodeBlock> defaultBlocks = new ArrayList<>();
//    SJasmCodeBlock currentCodeBlock = null;
    
//    HashMap<Integer,CodePage> pages = null;
    
    List<CodeStatement> enhancedJrList = new ArrayList<>();
    List<CodeStatement> enhancedDjnzList = new ArrayList<>();
    
    List<SJasmOutput> outputFiles = new ArrayList<>();
    SJasmOutput currentOutput = null;
    
    public SjasmDialect(MDLConfig a_config) {
        config = a_config;

        config.lineParser.tokensPreventingTextMacroExpansion.add("define");
        config.lineParser.tokensPreventingTextMacroExpansion.add("xdefine");
        config.lineParser.tokensPreventingTextMacroExpansion.add("assign");
        config.lineParser.tokensPreventingTextMacroExpansion.add("ifdef");

        config.lineParser.addKeywordSynonym("byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("dword", config.lineParser.KEYWORD_DD);
        config.lineParser.addKeywordSynonym("=", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym(".equ", config.lineParser.KEYWORD_EQU);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DS);
        
        config.preProcessor.macroSynonyms.put("endmacro", config.preProcessor.MACRO_ENDM);
        
        config.warning_jpHlWithParenthesis = false;    // I don't think sjasm supports "jp hl"
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.keywordsHintingALabel.add("#");
        config.lineParser.keywordsHintingALabel.add("field");
        config.lineParser.keywordsHintingALabel.add(":=");
        config.lineParser.allowIncludesWithoutQuotes = true;
        config.lineParser.macroDefinitionStyle = LineParser.MACRO_MACRO_NAME_ARGS;
        config.lineParser.allowNumberLabels = true;
        
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("high");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("low");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add(":");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("::");
                
        // We define it as a dialectMacro instead of as a synonym of "REPT", as it has some special syntax for
        // indicating the current iteration
        config.preProcessor.dialectMacros.put("repeat", "endrepeat");
        config.preProcessor.dialectMacros.put("ifexists", "endif");
        config.preProcessor.dialectMacros.put("ifnexists", "endif");
        config.preProcessor.dialectMacros.put("ifnexists", "endif");
        config.preProcessor.dialectMacros.put("ifdif", "endif");
        config.preProcessor.dialectMacros.put("ifdifi", "endif");
        config.macrosToEvaluateEagerly.add("ifexists");
        config.macrosToEvaluateEagerly.add("ifnexists");
        config.macrosToEvaluateEagerly.add("ifdif");
        config.macrosToEvaluateEagerly.add("ifdifi");
        
        config.preProcessor.dialectIfs.add("ifexists");
        config.preProcessor.dialectIfs.add("ifnexists");
        config.preProcessor.dialectIfs.add("ifdif");
        config.preProcessor.dialectIfs.add("ifdifi");
        
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
        addFakeInstruction("LD IX,BC", "ld ixl,c\nld ixh,b");
        addFakeInstruction("LD IX,DE", "ld ixl,e\nld ixh,d");
        addFakeInstruction("LD IX,HL", "push hl\npop ix");
        addFakeInstruction("LD IX,IX", "ld ixl,ixl\nld ixh,ixh");
        addFakeInstruction("LD IX,IY", "push iy\npop ix");
        addFakeInstruction("LD IY,BC", "ld iyl,c\nld iyh,b");
        addFakeInstruction("LD IY,DE", "ld iyl,e\nld iyh,d");
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
        config.tokenizer.stringEscapeSequences.put("\\", "\\");
        config.tokenizer.stringEscapeSequences.put("?", "\u0063");
        config.tokenizer.stringEscapeSequences.put("'", "'");
        config.tokenizer.stringEscapeSequences.put("\"", "\"");
        config.tokenizer.stringEscapeSequences.put("a", "\u0007");
        config.tokenizer.stringEscapeSequences.put("b", "\u0008");
        config.tokenizer.stringEscapeSequences.put("d", "\u0127");
        config.tokenizer.stringEscapeSequences.put("e", "\u0027");
        config.tokenizer.stringEscapeSequences.put("f", "\u0012");
        config.tokenizer.stringEscapeSequences.put("n", "\n");
        config.tokenizer.stringEscapeSequences.put("r", "\r");
        config.tokenizer.stringEscapeSequences.put("t", "\t");
        config.tokenizer.stringEscapeSequences.put("v", "\u0011");
        config.lineParser.applyEscapeSequencesToIncludeArguments = false;
        
        currentPages.clear();
        currentPages.add(0);  // page 0 by default
        
        // default output:
        currentOutput = new SJasmOutput("", null, null);
        outputFiles.add(currentOutput);
//        currentCodeBlock = currentOutput.defaultBlock;
        
        /*
        forbiddenLabelNames.add("struct");
        forbiddenLabelNames.add("ends");
        forbiddenLabelNames.add("byte");
        forbiddenLabelNames.add("defb");
        forbiddenLabelNames.add("word");
        forbiddenLabelNames.add("defw");
        forbiddenLabelNames.add("dword");
        forbiddenLabelNames.add("map");
        forbiddenLabelNames.add("endmap");
        forbiddenLabelNames.add("field");
        forbiddenLabelNames.add("assert");
        forbiddenLabelNames.add("incdir");
        forbiddenLabelNames.add("output");
        forbiddenLabelNames.add("defpage");
        forbiddenLabelNames.add("code");
        forbiddenLabelNames.add("align");
        forbiddenLabelNames.add("module");
        forbiddenLabelNames.add("endmodule");
        */
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
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("phase")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("dephase")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("abyte")) return true;

        for(SjasmStruct s:structs) {
            if (tokens.get(0).equals(s.name)) return true;
        }
        return false;
    }
    
    
    @Override
    public boolean parseLine(List<String> tokens, List<CodeStatement> l, SourceLine sl,
            CodeStatement s, CodeStatement previous, SourceFile source, CodeBase code) 
    {
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
            s.type = CodeStatement.STATEMENT_CONSTANT;
            SourceConstant c = new SourceConstant(struct.name, struct.name, null, s, config);
            s.label = c;
            if (code.addSymbol(c.name, c) != 1) return false;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) {
            tokens.remove(0);
            if (struct.file == null) {
                config.error("ends outside of a struct in " + sl);
                return false;
            }
            if (struct.file != source) {
                config.error("struct split among multiple files is not supported in " + sl);
                return false;
            }
            
            // Transform the struct into equ definitions with local labels:
            int offset = 0;
            int start = source.getStatements().indexOf(struct.start) + 1;
            for (int i = start; i < source.getStatements().size(); i++) {
                CodeStatement s2 = source.getStatements().get(i);
                int offset_prev = offset;
                switch (s2.type) {
                    case CodeStatement.STATEMENT_NONE:
                        break;
                    case CodeStatement.STATEMENT_DATA_BYTES:
                    case CodeStatement.STATEMENT_DATA_WORDS:
                    case CodeStatement.STATEMENT_DATA_DOUBLE_WORDS:
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
                        config.error("Unsupported statement (type="+s2.type+") inside a struct definition in " + sl);
                        return false;
                }
                if (s2.label != null) {
                    s2.type = CodeStatement.STATEMENT_CONSTANT;
                    s2.label.exp = Expression.constantExpression(offset_prev, config);
                } else {
                    s2.type = CodeStatement.STATEMENT_NONE;
                }                
            }

            // Record the struct for later:
            struct.start.label.exp = Expression.constantExpression(offset, config);
            structs.add(struct);
            config.lineParser.keywordsHintingALabel.add(struct.name);
            config.lineParser.popLabelPrefix();
            struct = null;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) {
            tokens.remove(0);
            // just ignore
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("map")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            mapCounterStack.add(0, mapCounter);
            mapCounter = exp.evaluateToInteger(s, code, false);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmap")) {
            tokens.remove(0);
            mapCounter = mapCounterStack.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if ((tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("field")) ||
            tokens.get(0).startsWith("#")) {
            if (tokens.get(0).startsWith("#") && tokens.get(0).length() > 1) {
                // this is a "#" plus a number attached together inside of a "map" group...
                if (config.warning_ambiguous) {
                    config.warn(tokens.get(0) + " is ambiguous syntax, please add a space between the # token and the field size to differentiate it from a hex constant in " + sl);
                }
                tokens.set(0, tokens.get(0).substring(1));
            } else {
                tokens.remove(0);
            }
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            if (s.label == null) {
                config.error("Field expression does not have a label in " + sl);
                return false;
            }
            if (struct != null) {
                s.label.exp = exp;
            } else {
                s.label.exp = Expression.constantExpression(mapCounter, Expression.RENDER_AS_16BITHEX, config);
            }
            s.type = CodeStatement.STATEMENT_CONSTANT;
            mapCounter += exp.evaluateToInteger(s, code, false);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            Integer value = exp.evaluateToInteger(s, code, false);
            if (value == null || value == Expression.FALSE) {
                config.error("Assertion failed in " + sl);
                return false;
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("incdir")) {
            tokens.remove(0);
            String folder = "";
            while(!tokens.isEmpty()) {
                if (config.tokenizer.isSingleLineComment(tokens.get(0)) || 
                    config.tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                folder += tokens.remove(0);
            }

            // Make sure we don't have a windows/Unix path separator problem:
            if (folder.contains("\\")) folder = folder.replace("\\", File.separator);
            
            File path = new File(config.lineParser.pathConcat(source.getPath(), folder));
            config.includeDirectories.add(path);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) {
            tokens.remove(0);
            String fileName = "";

            while(!tokens.isEmpty()) {
                if (config.tokenizer.isSingleLineComment(tokens.get(0)) || 
                    config.tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                fileName += tokens.remove(0);
            }
            
            if (fileName.startsWith("\"")) fileName = fileName.substring(1);
            if (fileName.endsWith("\"")) fileName = fileName.substring(0, fileName.length()-1);
            
            currentOutput = new SJasmOutput("_output"+outputFiles.size(), fileName, s);
            outputFiles.add(currentOutput);
            
            linesToKeepIfGeneratingDialectAsm.add(s);
                        
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);            
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defpage")) {
            tokens.remove(0);
            Expression pageExp = config.expressionParser.parse(tokens, s, previous, code);
            Expression pageStartExp = null;
            Expression pageSizeExp = null;
            if (pageExp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                pageStartExp = config.expressionParser.parse(tokens, s, previous, code);
                if (pageStartExp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                pageSizeExp = config.expressionParser.parse(tokens, s, previous, code);
                if (pageSizeExp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
            }
            int pageNumber = pageExp.evaluateToInteger(s, code, false);
            CodePage page = new CodePage(s, pageStartExp, pageSizeExp);
            if (currentOutput.pages.containsKey(pageNumber)) {
                config.warn("Redefining page " + pageNumber + " in " + sl);
            }
            currentOutput.pages.put(pageNumber, page);
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("code")) {
            tokens.remove(0);
            Expression addressExp = null;
            Expression alignmentExp = null;
            if (!tokens.isEmpty() && tokens.get(0).equals("?")) {
                config.error("Unsupported form of code keyword in " + sl);
                return false;
            }                    
            if (!tokens.isEmpty() && tokens.get(0).equals("@")) {
                tokens.remove(0);
                addressExp = config.expressionParser.parse(tokens, s, previous, code);
                if (addressExp == null) {
                    config.error("Cannot parse expression in " + sl);
                    return false;
                }
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) tokens.remove(0);
            }
            if (!tokens.isEmpty() && tokens.get(0).startsWith("#")) {
                if (tokens.get(0).equals("#")) {
                    tokens.remove(0);
                } else {
                    // remove the '#' character:
                    tokens.set(0, tokens.get(0).substring(1));
                }
                
                alignmentExp = config.expressionParser.parse(tokens, s, previous, code);
                if (alignmentExp == null) {
                    config.error("Could not determine alignment processing code keyword in " + sl);
                    return false;                    
                }                
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) tokens.remove(0);
            }
            if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("page")) {
                tokens.remove(0);
                
                // check if it's something of the form: "1..5":
                if (!tokens.isEmpty()) {
                    String pageToken = tokens.get(0);
                    int idx = pageToken.indexOf("..");
                    if (idx != -1) {
                        tokens.remove(0);
                        String token1 = pageToken.substring(0, idx);
                        String token2 = pageToken.substring(idx+2);
                        int page1 = Integer.parseInt(token1);
                        int page2 = Integer.parseInt(token2);
                        if (page1 > page2) {
                            config.error("Placing code in more than one possible page not yet supported in " + sl);
                            return false;
                        }
                        currentPages.clear();
                        for(int i = page1; i<=page2; i++) {
                            currentPages.add(i);
                        }
                    } else {                
                        Expression pageExp = config.expressionParser.parse(tokens, s, previous, code);
                        if (pageExp == null) {
                            config.error("Could not determine current page processing code keyword in " + sl);
                            return false;                    
                        }
                        int page = pageExp.evaluateToInteger(s, code, false);
                        currentPages.clear();
                        currentPages.add(page);
                    }
                } else {
                    config.error("Could not determine current page processing code keyword in " + sl);
                    return false;                    
                }
            }            
                        
            currentOutput.codeBlocks.add(new SJasmCodeBlock(s, currentPages, addressExp, alignmentExp));
            
            // ignore (but still add the statement, so we know where the codeblock starts)
            s.type = CodeStatement.STATEMENT_NONE;
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) {
            tokens.remove(0);
            if (!tokens.isEmpty()) {
                String pageToken = tokens.get(0);
                int idx = pageToken.indexOf("..");
                if (idx != -1) {
                    tokens.remove(0);
                    String token1 = pageToken.substring(0, idx);
                    String token2 = pageToken.substring(idx+2);
                    int page1 = Integer.parseInt(token1);
                    int page2 = Integer.parseInt(token2);
                    if (page1 > page2) {
                        config.error("Specifying more than one possible page not yet supported in " + sl);
                        return false;
                    }
                    for(int i = page1; i<=page2; i++) {
                        currentPages.add(i);
                    }
                } else {
                    Expression pageExp = null;
                    pageExp = config.expressionParser.parse(tokens, s, previous, code);
                    if (pageExp == null) {
                        config.error("Cannot parse expression in " + sl);
                        return false;
                    }
                    int page = pageExp.evaluateToInteger(s, code, false);
                        currentPages.clear();
                        currentPages.add(page);
                }
                
                Expression addressExp = null;
                currentOutput.codeBlocks.add(new SJasmCodeBlock(s, currentPages, addressExp, null));
                // parse it as an "org"
                s.type = CodeStatement.STATEMENT_NONE;
                
                linesToKeepIfGeneratingDialectAsm.add(s);
                
                return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
            } else {
                config.error("Missing page number in " + sl);
                return false;                
            }
        }        
        if (tokens.size() >= 3 && tokens.get(0).equalsIgnoreCase("[")) {
            tokens.remove(0);
            Expression numberExp = config.expressionParser.parse(tokens, s, previous, code);
            if (tokens.isEmpty() || !tokens.get(0).equals("]")) {
                config.error("Cannot parse line in " + sl);
                return false;
            }
            tokens.remove(0);
            int number = numberExp.evaluateToInteger(s, code, false);
            l.clear();
            for(int i = 0;i<number;i++) {
                List<String> tokensCopy = new ArrayList<>();
                tokensCopy.addAll(tokens);
                // we need to parse it every time, to create multiple different copies of the statements:
                config.expressionParser.sjasmConterVariables.add(i);
                List<CodeStatement> l2 = config.lineParser.parse(tokensCopy, sl, source, previous, code, config);
                config.expressionParser.sjasmConterVariables.remove(config.expressionParser.sjasmConterVariables.size()-1);
                if (l2 == null) {
                    config.error("Cannot parse line in " + sl);
                    return false;
                }

                l.addAll(l2);
            }
            return true;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(":=")) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode in " + sl);
                return false;
            }
            
            tokens.remove(0);
            s.label.resolveEagerly = true;
            if (!config.lineParser.parseEqu(tokens, l, sl, s, previous, source, code)) return false;
            s.label.clearCache();
            Integer value = s.label.exp.evaluateToInteger(s, code, false);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return false;
            }
            s.label.exp = Expression.constantExpression(value, config);
            
            // these variables should not be part of the source code:
            l.clear();
            return true;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) {
            tokens.remove(0);
            if (!config.lineParser.parseData(tokens, "db", l, sl, s, previous, source, code)) return false;
            // insert a "0" at the end of each string:
            List<Expression> newData = new ArrayList<>();
            for(Expression exp:s.data) {
                newData.add(exp);
                newData.add(Expression.constantExpression(0, config));
            }
            s.data = newData;
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse expression in " + sl);
                return false;
            }
            s.type = CodeStatement.STATEMENT_DEFINE_SPACE;
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
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }   
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("module")) {
            tokens.remove(0);
            String moduleName = tokens.remove(0);
            
            modules.add(0, moduleName);
            config.lineParser.pushLabelPrefix(moduleName + ".");
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
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
                    config.error("Cannot close unexistent module in " + sl);
                    return false;
                }
            }
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("jr.")) {
            // store in a list:
            enhancedJrList.add(s);

            // parse as "jp":
            tokens.remove(0);
            if (config.lineParser.parseCPUOp(tokens, "jp", sl, l, previous, source, code)) return true;
            return false;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("jp.")) {
            // store in a list:
            enhancedJrList.add(s);

            // parse as "jp":
            tokens.remove(0);
            if (config.lineParser.parseCPUOp(tokens, "jp", sl, l, previous, source, code)) return true;
            return false;
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("djnz.")) {
            // store in a list:
            enhancedDjnzList.add(s);

            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse argument in " + sl);
                return false;                
            }
            
            // parse as "dec b; jp nz,label":
            {
                CodeStatement auxiliaryS = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, source, config);
                List<Expression> auxiliaryArguments = new ArrayList<>();
                auxiliaryArguments.add(Expression.symbolExpression("b", auxiliaryS, code, config));
                List<CPUOp> op_l = config.opParser.parseOp("dec", auxiliaryArguments, s, previous, code);
                if (op_l == null || op_l.size() != 1) return false;
                auxiliaryS.op = op_l.get(0);
                l.add(0, auxiliaryS);
            }
            {
                List<Expression> auxiliaryArguments = new ArrayList<>();
                auxiliaryArguments.add(Expression.symbolExpression("nz", s, code, config));
                auxiliaryArguments.add(exp);
                List<CPUOp> op_l = config.opParser.parseOp("jp", auxiliaryArguments, s, previous, code);
                if (op_l == null || op_l.size() != 1) return false;
                s.type = CodeStatement.STATEMENT_CPUOP;
                s.op = op_l.get(0);
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && 
                (tokens.get(0).equalsIgnoreCase("define") ||
                 tokens.get(0).equalsIgnoreCase("xdefine") ||
                 tokens.get(0).equalsIgnoreCase("assign"))) {
            String keyword = tokens.remove(0);

            // Text macros:

            // read variable name:
            String macroName = tokens.remove(0);
            if (!config.caseSensitiveSymbols) macroName = macroName.toLowerCase();
            
            List<String> macroArguments = new ArrayList<>();
            List<String> macroTokens = new ArrayList<>();

            // check if it's a define with parameters:
            if (!tokens.isEmpty() && tokens.get(0).equals("(")) {
                // define with arguments:
                tokens.remove(0);
                while(true) {
                    macroArguments.add(tokens.remove(0));
                    if (tokens.isEmpty()) {
                        config.error(keyword + ": Cannot parse line " + sl);
                        return false;                            
                    }
                    if (tokens.get(0).equals(")")) {
                        tokens.remove(0);
                        break;
                    }
                    if (tokens.get(0).equals(",")) {
                        tokens.remove(0);
                    }
                }
            }
                    
            // parameters parsed, parse the body:
            for(String token:tokens) {
                if (config.tokenizer.isSingleLineComment(token)) break;
                macroTokens.add(token);
            }
            tokens.clear();

            if (keyword.equalsIgnoreCase("assign")) {
                // evaluate expression, and just assign the result:
                config.preProcessor.expandTextMacros(macroTokens, s, sl);
                Expression exp = config.expressionParser.parse(macroTokens, s, previous, code);
                if (exp == null) {
                    config.error(keyword + ": Cannot parse line " + sl);
                    return false;                            
                }
                Object value = exp.evaluate(s, code, true);
                if (value == null) {
                    config.error(keyword + ": Cannot evaluate expression in " + sl);
                    return false;                            
                }
                String valueString = value.toString();
                macroTokens = config.tokenizer.tokenize(valueString);
            } else if (keyword.equalsIgnoreCase("xdefine")) {
                // evaluate the text macros within the definition
                config.preProcessor.expandTextMacros(macroTokens, s, sl);
            }
            
            TextMacro macro = new TextMacro(macroName, macroArguments, macroTokens, s);
            config.preProcessor.addTextMacro(macro);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }      
        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("phase")) {
            tokens.remove(0);
            
            // parse as an "org":
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse phase address in " + sl);
                return false;
            }            
            s.type = CodeStatement.STATEMENT_ORG;
            s.org = exp;
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("dephase")) {
            tokens.remove(0);
            
            // ignore for now...
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        
        if (parseAbyte(tokens, l, sl, s, previous, source, code)) return true;

        return parseLineStruct(tokens, l, sl, s, previous, source, code);
    }


    @Override
    public boolean parseFakeCPUOps(List<String> tokens, SourceLine sl, List<CodeStatement> l, CodeStatement previous, SourceFile source, CodeBase code) 
    {
        // This function only adds the additional instructions beyond the first one. So, it will leave in "tokens", the set of
        // tokens necessary for MDL's regular parser to still parse the first op
        CodeStatement s = l.get(0);
        
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
                if (tokens.get(0).equalsIgnoreCase("pop")) {
                    // for "pop", we add them in reverse:
                    regpairs.add(0, regpair);                    
                } else {
                    regpairs.add(regpair);
                }
                idx++;
                if (tokens.size()<=idx) break;
                if (config.tokenizer.isSingleLineComment(tokens.get(idx))) break;
                if (!tokens.get(idx).equals(",")) {
                    process = false;
                    break;
                }
                idx++;
            }
            if (process && regpairs.size()>1) {
                int toremove = (regpairs.size()-1)*2;
                for(int i = 0;i<toremove;i++) tokens.remove(2);
                
                // we overwrite the argument of the first, since in the case of a pop, the order might have changed:
                tokens.set(1, regpairs.get(0));
                
                for(int i = 1;i<regpairs.size();i++) {
                    CodeStatement auxiliaryS = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, source, config);
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
                CodeStatement auxiliaryS = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, source, config);
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
                CodeStatement auxiliaryS = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, source, config);
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
    public Integer evaluateExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code, boolean silent)
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
                config.error("':' operator used on a non-symbol expression in " + s.sl);
                return null;
            }
            Integer page = symbolPage.get(args.get(0).symbolName);
            if (page == null) {
                config.error("Unknown page of symbol "+args.get(0).symbolName+" in " + s.sl);
                return null;
            }
            return page;
        }
        if (functionName.equalsIgnoreCase("::") && args.size() == 1) {
            if (!args.get(0).evaluatesToIntegerConstant()) {
                config.error("Argument to '::' could not be evaluated to an integer page in " + s.sl);
                return null;
            }
            Integer page = args.get(0).evaluateToInteger(s, code, silent);
            if (page == null) {
                config.error("Argument to '::' could not be evaluated to an integer page in " + s.sl);
                return null;
            }
            
            SJasmOutput output = null;
            if (outputFiles.get(0).reconstructedFile == null) {
                // we are still parsing:
                output = currentOutput;
            } else {
                for(SJasmOutput o:outputFiles) {
                    if (o.reconstructedFile != null) {
                        if (o.reconstructedFile.getStatements().contains(s)) {
                            output = o;
                            break;
                        }
                    }
                }
            }
            if (output == null) {
                // We don't know how to evaluate it yet:
                return null;
            }
            
            Expression pageSize = Expression.parenthesisExpression(
                    Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                            Expression.symbolExpression("__sjasm_page_"+page+output.ID+"_end", s, code, config), 
                            Expression.symbolExpression("__sjasm_page_"+page+output.ID+"_start", s, code, config), config), "(", config);
            return pageSize.evaluateToInteger(s, code, silent);
        }
        return null;
    }

    
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code) {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            Expression exp = Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, Expression.RENDER_AS_16BITHEX, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
//            exp.originalDialectExpression = ???;
            return exp;
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, Expression.RENDER_AS_16BITHEX, config), config);
        }
        if (functionName.equalsIgnoreCase("::") && args.size() == 1) {
            if (!args.get(0).evaluatesToIntegerConstant()) {
                config.error("Argument to '::' could not be evaluated to an integer page in " + s.sl);
                return null;
            }
            Integer page = args.get(0).evaluateToInteger(s, code, true);
            if (page == null) {
                config.error("Argument to '::' could not be evaluated to an integer page in " + s.sl);
                return null;
            }
            
            SJasmOutput output = null;
            if (outputFiles.get(0).reconstructedFile == null) {
                // we are still parsing:
                output = currentOutput;
            } else {
                for(SJasmOutput o:outputFiles) {
                    if (o.reconstructedFile != null) {
                        if (o.reconstructedFile.getStatements().contains(s)) {
                            output = o;
                            break;
                        }
                    }
                }
            }
            if (output == null) {
                // We don't know how to evaluate it yet:
                return null;
            }
            
            Expression pageSize = Expression.parenthesisExpression(
                    Expression.operatorExpression(Expression.EXPRESSION_SUB, 
                            Expression.symbolExpression("__sjasm_page_"+page+output.ID+"_end", s, code, config), 
                            Expression.symbolExpression("__sjasm_page_"+page+output.ID+"_start", s, code, config), config), "(", config);
            return pageSize;
        }
        return null;
    }
    
    
    @Override
    public MacroExpansion instantiateMacro(SourceMacro macro, List<Expression> args, CodeStatement macroCall, CodeBase code)
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
            int iterations = iterations_tmp;
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
//            config.trace("<------ start: instantiateMacro: repeat ------->");
//            for(SourceLine tmp:lines2) {
//                config.trace(tmp.line);
//            }
//            config.trace("<------ end: instantiateMacro: repeat ------->");
            return me;
            
        } else if (macro.name.equals("ifexists")) {
            String fileName;
            if (args.size() == 1 && args.get(0).evaluatesToStringConstant()) {
                fileName = args.get(0).evaluateToString(macro.definingStatement, code, true);
            } else if (args.size() == 1) {
                // this macro allows for a file name without quotes, so, reconstruct the name:
                String tmp = args.get(0).toString();
                List<String> tokens = config.tokenizer.tokenize(tmp);
                fileName = "";
                for(String token:tokens) fileName += token;
            } else {
                config.error("Could not extract the argument of ifexists macro with arguments: " + args);
                return null;
            }
            String path = config.lineParser.resolveIncludePath(fileName, macroCall.source, macroCall.sl);
            if (path == null) {
                // do not expand the if:
                return me;
            } else {
                // expand the if:
                lines2.addAll(macro.lines);
                return me;
            }

        } else if (macro.name.equals("ifnexists")) {
            String fileName;
            if (args.size() == 1 && args.get(0).evaluatesToStringConstant()) {
                fileName = args.get(0).evaluateToString(macro.definingStatement, code, true);
            } else if (args.size() == 1) {
                // this macro allows for a file name without quotes, so, reconstruct the name:
                String tmp = args.get(0).toString();
                List<String> tokens = config.tokenizer.tokenize(tmp);
                fileName = "";
                for(String token:tokens) fileName += token;
            } else {
                config.error("Could not extract the argument of ifexists macro with arguments: " + args);
                return null;
            }
            String path = config.lineParser.resolveIncludePath(fileName, macroCall.source, macroCall.sl);
            if (path != null) {
                // do not expand the if:
                return me;
            } else {
                // expand the if:
                lines2.addAll(macro.lines);
                return me;
            }
            
        } else if (macro.name.equals("ifdif")) {
            if (args.size() == 2) {
                String arg1 = (args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG ?
                                args.get(0).registerOrFlagName : 
                                args.get(0).evaluate(macroCall, code, true) + "");
                String arg2 = (args.get(1).type == Expression.EXPRESSION_REGISTER_OR_FLAG ?
                                args.get(1).registerOrFlagName : 
                                args.get(1).evaluate(macroCall, code, true) + "");
                if (arg1.equals(arg2)) {
                    if (macro.elseLines != null) {
                        lines2.addAll(macro.elseLines);
                    }
                    return me;
                } else {
                    lines2.addAll(macro.lines);
                    return me;                    
                }
            } else {
                config.error("ifdif should have two arguments, instead of: " + args);
                return null;
            }

        } else if (macro.name.equals("ifdifi")) {
            if (args.size() == 2) {
                String arg1 = (args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG ?
                                args.get(0).registerOrFlagName : 
                                args.get(0).evaluate(macroCall, code, true) + "");
                String arg2 = (args.get(1).type == Expression.EXPRESSION_REGISTER_OR_FLAG ?
                                args.get(1).registerOrFlagName : 
                                args.get(1).evaluate(macroCall, code, true) + "");
                if (arg1.equalsIgnoreCase(arg2)) {
                    if (macro.elseLines != null) {
                        lines2.addAll(macro.elseLines);
                    }
                    return me;
                } else {
                    lines2.addAll(macro.lines);
                    return me;                    
                }
            } else {
                config.error("ifdif should have two arguments, instead of: " + args);
                return null;
            }
            
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
                
        // set the starting statement of the first default block:
        SourceFile oldMain = code.outputs.get(0).main;
        SJasmOutput defaultOutput = outputFiles.get(0);
        defaultOutput.startStatement = oldMain.getStatements().get(0);
        defaultOutput.defaultBlock.startStatement = defaultOutput.startStatement;
        
        // Create a new source file per output, and add all the code blocks:
        code.getSourceFiles().clear();
        code.outputs.clear();
        
        for(SJasmOutput output:outputFiles) {
            String fileName = oldMain.getPath() + output.fileName + ".asm";
            output.reconstructedFile = new SourceFile(fileName, null, null, code, config);
            code.addSourceFile(output.reconstructedFile);
            code.addOutput(output.fileName, output.reconstructedFile);
            if (!reconstructOutputFile(output, code)) {
                return false;
            }
        }
        
        if (outputFiles.size() > 1 && defaultOutput.reconstructedFile.sizeInBytes(code, true, true, true) == 0) {
            SJasmOutput firstdefined = outputFiles.get(1);
            firstdefined.reconstructedFile.getStatements().addAll(0, defaultOutput.reconstructedFile.getStatements());
            code.getSourceFiles().remove(defaultOutput.reconstructedFile);
            code.outputs.remove(0);
        }
        return true;
    }
        
        
    public boolean reconstructOutputFile(SJasmOutput output, CodeBase code)
    {
        // Reorganize all the "code" blocks into the different pages:
        SJasmCodeBlock initialBlock = new SJasmCodeBlock(null, null, null, null);
        SJasmCodeBlock currentBlock = initialBlock;

        {
            // get the very first statement, and iterate over all the rest:
            CodeStatement s = output.startStatement;
            SJasmOutput nextOutput = null;
            if (outputFiles.indexOf(output) < outputFiles.size() - 1) {
                nextOutput = outputFiles.get(outputFiles.indexOf(output) + 1);
            }
            
            while(s != null) {
                if (s.type == CodeStatement.STATEMENT_INCLUDE) {
                    // make sure we do not lose te label:
                    if (s.label != null) {
                        CodeStatement labelS = new CodeStatement(CodeStatement.STATEMENT_NONE, s.sl, s.source, config);
                        labelS.label = s.label;
                        currentBlock.statements.add(labelS);
                    }
                    s = s.include.getStatements().get(0);
                } else {
                    // check if we have reached the next output:
                    if (nextOutput != null && nextOutput.startStatement == s) {
                        break;
                    }
                    
                    // See if a new block starts:
                    SJasmCodeBlock block = blockStartingAt(s, output);
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
        resolveEnhancedJumps(code, output);

        // Assign blocks to pages, first those that have a defined address, then the rest:
        // Start with those that have a defined address:
        List<SJasmCodeBlock> blocksToAssign = new ArrayList<>();
        // these are those that are created by default, before defining any "code" block,
        // and should go at the very beginning:
        List<SJasmCodeBlock> initialBlocks = new ArrayList<>();
        initialBlocks.add(output.defaultBlock);
        for(SJasmCodeBlock b:output.codeBlocks) {
            Integer address = null;
            if (b.address != null) address = b.address.evaluateToInteger(b.startStatement, code, true);
            if (address == null) {
                blocksToAssign.add(b);
            } else {
                boolean added = false;
                for(Integer pageIdx:b.candidatePages) {
                    CodePage page = output.pages.get(pageIdx);
                    if (page.addBlock(b, code, config)) {
                        // assign all the symbols in this block to this page:
                        for(CodeStatement bs:b.statements) {
                            if (bs.label != null) {
                                symbolPage.put(bs.label.name, pageIdx);
                            }
                        }
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    Integer blockSize = b.size(code);
                    if (blockSize == null) {
                        config.error("Could not calculate size of a block to add it to pages " + b.candidatePages + "!");
                    } else {
                        config.error("Could not add block of size " + blockSize + " to pages " + b.candidatePages + "!");
                    }
                    return false;
                }
            }
        }

        // Sort the rest of codeblocks by size, and assign them to pages where they fit:
        Collections.sort(blocksToAssign, new Comparator<SJasmCodeBlock>() {
            @Override
            public int compare(SJasmCodeBlock b1, SJasmCodeBlock b2) {
                Integer s1 = b1.size(code);
                Integer s2 = b2.size(code);
                if (s1 == null || s2 == null) return 0;
                return -Integer.compare(s1, s2);
            }
        });

        blocksToAssign.addAll(0, initialBlocks);

        for(SJasmCodeBlock b:blocksToAssign) {
            boolean added = false;
            for(Integer pageIdx:b.candidatePages) {
                CodePage page = output.pages.get(pageIdx);
                if (page == null && pageIdx == 0 && output.pages.isEmpty()) {
                    // no pages defined in the code, define a default page:
                    page = new CodePage(output.startStatement, 
                                        Expression.constantExpression(0, config), null);
                    output.pages.put(0, page);
                }
                if (page.addBlock(b, code, config)) {
                    // assign all the symbols in this block to this page:
                    for(CodeStatement bs:b.statements) {
                        if (bs.label != null) {
                            symbolPage.put(bs.label.name, pageIdx);
                        }
                    }
                    added = true;
                    break;
                }
            }
            if (!added) {
                Integer blockSize = b.size(code);
                if (blockSize == null) {
                    config.error("Could not calculate size of a block to add it to pages " + b.candidatePages + "!");
                } else {                   
                    config.error("Could not add block of size " + b.size(code) + " to pages " + b.candidatePages + "!");
                }
                return false;
            }
        }

        // start by adding initialBlock:
        for(CodeStatement s:initialBlock.statements) {
            s.source = output.reconstructedFile;
            output.reconstructedFile.addStatement(s);
        }

        // add the rest of blocks, inserting appropriate spacing in between:
        List<Integer> pageIndexes = new ArrayList<>();
        pageIndexes.addAll(output.pages.keySet());
        Collections.sort(pageIndexes);

        for(int idx:pageIndexes) {
            CodePage page = output.pages.get(idx);
            if (!output.reconstructedFile.getStatements().isEmpty() || 
                page.start.type != Expression.EXPRESSION_INTEGER_CONSTANT ||
                page.start.integerConstant != 0) {
                CodeStatement org = new CodeStatement(CodeStatement.STATEMENT_ORG, 
                        new SourceLine("", output.reconstructedFile, output.reconstructedFile.getStatements().size()+1), output.reconstructedFile, config);
                org.org = page.start;
                output.reconstructedFile.addStatement(org);
                
                auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(org);
            }
            Integer pageStart = page.start.evaluateToInteger(page.s, code, true);
            if (pageStart == null) {
                config.error("Cannot evaluate " + page.start + " in " + page.s.sl);
                return false;
            }
            Integer pageSize = (page.size == null ? null:page.size.evaluateToInteger(page.s, code, true));
            int currentAddress = pageStart;

            // Add page start labels to each page:
            CodeStatement pageStartStatement = new CodeStatement(CodeStatement.STATEMENT_NONE, page.s.sl, output.reconstructedFile, config);
            pageStartStatement.label = new SourceConstant("__sjasm_page_" + idx + output.ID+"_start", "__sjasm_page_" + idx + output.ID+"_start", 
                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, pageStartStatement, code, config), pageStartStatement, config);
            output.reconstructedFile.addStatement(pageStartStatement);
            code.addSymbol(pageStartStatement.label.name, pageStartStatement.label);
            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(pageStartStatement);

            for(SJasmCodeBlock block:page.blocks) {
                if (block.actualAddress > currentAddress) {
                    // insert space:
                    CodeStatement space = new CodeStatement(CodeStatement.STATEMENT_DEFINE_SPACE, 
                            new SourceLine("", output.reconstructedFile, output.reconstructedFile.getStatements().size()+1), output.reconstructedFile, config);
                    space.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                    Expression.constantExpression(block.actualAddress, config),
                                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, space, code, config),
                                    config);
                    space.space_value = Expression.constantExpression(0, config);
                    output.reconstructedFile.addStatement(space);
                    config.debug("inserting space (end of block) of " + (block.actualAddress-currentAddress));
                }
                for(CodeStatement s:block.statements) {
                    s.source = output.reconstructedFile;
                    output.reconstructedFile.addStatement(s);
                }
                code.resetAddresses();
                currentAddress = block.actualAddress + block.size(code);
            }
            if (pageSize != null && currentAddress < pageStart + pageSize) {
                // insert space:
                CodeStatement space = new CodeStatement(CodeStatement.STATEMENT_DEFINE_SPACE, new SourceLine("", output.reconstructedFile, output.reconstructedFile.getStatements().size()+1), output.reconstructedFile, config);
                space.space = Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                Expression.constantExpression(pageStart + pageSize, config),
                                Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, space, code, config),
                                config);
                space.space_value = Expression.constantExpression(0, config);
                output.reconstructedFile.addStatement(space);
                auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(space);
                config.debug("inserting space (end of page) of " + ((pageStart + pageSize) - currentAddress) + " from " + currentAddress + " to " + (pageStart + pageSize));
            } else {
                pageSize = currentAddress - pageStart;
            }

            // Add page end labels to each page:                
            CodeStatement pageEndStatement = new CodeStatement(CodeStatement.STATEMENT_NONE, page.s.sl, output.reconstructedFile, config);
            pageEndStatement.label = new SourceConstant("__sjasm_page_" + idx + output.ID+"_end", "__sjasm_page_" + idx + output.ID+"_end", 
                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, pageEndStatement, code, config), pageEndStatement, config);
            output.reconstructedFile.addStatement(pageEndStatement);
            code.addSymbol(pageEndStatement.label.name, pageEndStatement.label);
            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(pageEndStatement);

            config.debug("page " + idx + " from " + pageStart + " to " + (pageStart+pageSize));
        }
        
        code.resetAddresses();
        return true;
    }
        
    
    public SJasmCodeBlock findCodeBlock(CodeStatement s, SJasmOutput output)
    {
        if (output.defaultBlock.statements.contains(s)) return output.defaultBlock;
        for(SJasmCodeBlock cb:output.codeBlocks) {
            if (cb.statements.contains(s)) return cb;
        }
        return null;
    }
    

    public void resolveEnhancedJumps(CodeBase code, SJasmOutput output)
    {
        // resolve enhanced jumps:
        for(CodeStatement s:enhancedJrList) {
            SJasmCodeBlock b1 = findCodeBlock(s, output);
            SJasmCodeBlock b2 = null;
            if (s.op != null && !s.op.args.isEmpty()) {
                Expression label = s.op.args.get(s.op.args.size()-1);
                if (label.type == Expression.EXPRESSION_SYMBOL) {
                    SourceConstant c = code.getSymbol(label.symbolName);
                    if (c != null) {
                        b2 = findCodeBlock(c.definingStatement, output);
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
        for(CodeStatement s:enhancedDjnzList) {
            SJasmCodeBlock b1 = findCodeBlock(s, output);
            SJasmCodeBlock b2 = null;
            if (s.op != null && !s.op.args.isEmpty()) {
                CodeStatement previous = s.source.getPreviousStatementTo(s, code);
                if (previous.source == s.source && 
                        previous.op != null && 
                        previous.op.spec.getName().equalsIgnoreCase("dec")) {
                    Expression label = s.op.args.get(s.op.args.size()-1);
                    if (label.type == Expression.EXPRESSION_SYMBOL) {
                        SourceConstant c = code.getSymbol(label.symbolName);
                        if (c != null) {
                            b2 = findCodeBlock(c.definingStatement, output);
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
                                    b1.statements.remove(previous);
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
    
    
    SJasmCodeBlock blockStartingAt(CodeStatement s, SJasmOutput output)
    {
        if (output.defaultBlock.startStatement == s) return output.defaultBlock;
        for(SJasmCodeBlock b:output.codeBlocks) {
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
    public List<SourceLine> expandVariableNumberOfArgsMacro(List<Expression> args, CodeStatement macroCall, SourceMacro macro, CodeBase code, MDLConfig config)
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
            List<String> tokens = config.tokenizer.tokenizeIncludingBlanks(line2);
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
                List<String> tokens2 = config.tokenizer.tokenize(repeatStatement.line);
                tokens2.remove(0);  // skip "repeat"
                Expression exp = config.expressionParser.parse(tokens2, macroCall, macroCall.source.getPreviousStatementTo(macroCall, code), code);
                int nIterations = exp.evaluateToInteger(macroCall, code, false);
                for(int i = 0;i<nIterations;i++) {
                    for(SourceLine sl3:repeatLinesToExecute) {
                        List<String> tokens3 = config.tokenizer.tokenizeIncludingBlanks(sl3.line);
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
                            List<String> tokensRotate = config.tokenizer.tokenize(line3);
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
    
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code, boolean useOriginalNames, Path rootPath) {
        if (linesToKeepIfGeneratingDialectAsm.contains(s)) {
            return s.sl.line;
        }

        if (auxiliaryStatementsToRemoveIfGeneratingDialectasm.contains(s)) return "";
        
        return s.toStringUsingRootPath(rootPath, useOriginalNames, true);
    }    
    
    
    @Override
    public boolean supportsMultipleOutputs()
    {
        return true;
    }    
    
}

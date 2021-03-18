/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package parser.dialects;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import parser.LineParser;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;

/**
 *
 * @author santi
 */
public class SjasmPlusDialect extends SjasmDerivativeDialect implements Dialect
{
    public static class PrintRecord {
        String keyword;
        CodeStatement previousStatement;  // not the current, as it was probably not added to the file
        List<Expression> exp_l;
        
        public PrintRecord(String a_kw, CodeStatement a_prev, List<Expression> a_exp_l)
        {
            keyword = a_kw;
            previousStatement = a_prev;
            exp_l = a_exp_l;
        }
    }

    
    List<Integer> slotSizes = new ArrayList<>();
    List<Integer> pageSizes = new ArrayList<>();
    Integer currentSlot = null;
    
    // Addresses are not resolved until the very end, so, when printing values, we just queue them up here, and
    // print them all at the very end:
    List<PrintRecord> toPrint = new ArrayList<>();
    

    SjasmPlusDialect(MDLConfig a_config) {
        config = a_config;

        config.warning_jpHlWithParenthesis = true;
        config.macrosToEvaluateEagerly.add(config.preProcessor.MACRO_IFDEF);
        config.macrosToEvaluateEagerly.add(config.preProcessor.MACRO_IFNDEF);
        
        config.lineParser.addKeywordSynonym("byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("dword", config.lineParser.KEYWORD_DD);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym("block", config.lineParser.KEYWORD_DS);
                
        config.lineParser.keywordsHintingANonScopedLabel.add("=");
        
        config.warning_jpHlWithParenthesis = true;
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.allowIncludesWithoutQuotes = true;
        config.lineParser.macroDefinitionStyle = LineParser.MACRO_BOTH;
        config.lineParser.allowNumberLabels = true;
        config.lineParser.allowColonSeparatedInstructions = true;
        
        config.opParser.allowExtendedSjasmplusLDInstructions = true;
        
        config.expressionParser.addRegisterSynonym("xl", "ixl");
        config.expressionParser.addRegisterSynonym("lx", "ixl");
        config.expressionParser.addRegisterSynonym("xh", "ixh");
        config.expressionParser.addRegisterSynonym("hx", "ixh");
        config.expressionParser.addRegisterSynonym("yl", "iyl");
        config.expressionParser.addRegisterSynonym("ly", "iyl");
        config.expressionParser.addRegisterSynonym("yh", "iyh");
        config.expressionParser.addRegisterSynonym("hy", "iyh");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("high");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesis.add("low");        
        config.expressionParser.sjasmPlusCurlyBracketExpressions = true;
                        
        config.preProcessor.macroSynonyms.put("dup", config.preProcessor.MACRO_REPT);
        config.preProcessor.macroSynonyms.put("edup", config.preProcessor.MACRO_ENDR);
        
        // It is important that registers on the left-hand-side are capitalized (the right hand side does not matter):
        // Notice that these are similar, but not exactly identical than the definitions of sjasm. Hence, they are
        // separated. For example, "ld ix,bc", in sjasm expands to "ld ixl,c\nld ixh,b" but in sjasm expands to 
        // "ld ixh,b\nld ixl,c". There are about 10 instructions that differ like this.
        addFakeInstruction("RL BC", "rl c\nrl b");
        addFakeInstruction("RL DE", "rl e\nrl d");
        addFakeInstruction("RL HL", "rl l\nrl h");
        addFakeInstruction("RR BC", "rr b\nrr c");
        addFakeInstruction("RR DE", "rr d\nrr e");
        addFakeInstruction("RR HL", "rr h\nrr l");
        addFakeInstruction("SLA BC", "sla c\nrl b");
        addFakeInstruction("SLA DE", "sla e\nrl d");
        addFakeInstruction("SLA HL", "add hl,hl");
        addFakeInstruction("SLL A", "sli a");
        addFakeInstruction("SLL B", "sli b");
        addFakeInstruction("SLL C", "sli c");
        addFakeInstruction("SLL D", "sli d");
        addFakeInstruction("SLL E", "sli e");
        addFakeInstruction("SLL H", "sli h");
        addFakeInstruction("SLL L", "sli l");
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
        addFakeInstruction("ex AF,AF", "ex AF,AF'");
        addFakeInstruction("ex AF", "ex AF,AF'");
        addFakeInstruction("exa", "ex AF,AF'");

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
        config.tokenizer.curlyBracesAreComments = false;
        config.lineParser.applyEscapeSequencesToIncludeArguments = false;
        
//        forbiddenLabelNames.add("struct");
//        forbiddenLabelNames.add("ends");
//        forbiddenLabelNames.add("byte");
//        forbiddenLabelNames.add("defb");
//        forbiddenLabelNames.add("word");
//        forbiddenLabelNames.add("defw");
//        forbiddenLabelNames.add("dword");
//        forbiddenLabelNames.add("assert");
//        forbiddenLabelNames.add("output");
//        forbiddenLabelNames.add("align");
//        forbiddenLabelNames.add("module");
//        forbiddenLabelNames.add("endmodule");
//        forbiddenLabelNames.add("device");
//        forbiddenLabelNames.add("savebin");
    }
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code) {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("struct")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("module")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmodule")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("device")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("=")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("savebin")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("display")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("define")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("abyte")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("opt")) return true;

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
                    case CodeStatement.STATEMENT_DEFINE_SPACE:
                    {
                        Integer size = s2.space.evaluateToInteger(s2, code, true);
                        if (size == null) {
                            config.error("Cannot evaluate " + s2.space + " to an integer in " + s2.sl);
                            return false;
                        }
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

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) {
            // Just ignore ...
            while(!tokens.isEmpty()) {
                if (config.tokenizer.isSingleLineComment(tokens.get(0)) || 
                    config.tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                tokens.remove(0);
            }

            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
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
            Expression value = null;
            if (exp == null) {
                config.error("Cannot parse amount expression in " + sl);
                return false;
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                value = config.expressionParser.parse(tokens, s, previous, code);
                if (value == null) {
                    config.error("Cannot parse value expression in " + sl);
                    return false;
                }
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
            if (value != null) {
                s.space_value = value;
            } else {
                s.space_value = Expression.constantExpression(0, config);
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }   
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("module")) {
            tokens.remove(0);
            String moduleName = tokens.remove(0);
            
            modules.add(0, moduleName);
            config.lineParser.pushLabelPrefix(moduleName + ".");

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
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }    
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("device")) {
            tokens.remove(0);
            String deviceName = tokens.remove(0).toLowerCase();
            
            switch(deviceName) {
                case "none":
                    slotSizes.clear();
                    pageSizes.clear();
                    break;
                case "zxspectrum48":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum128":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<8;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum256":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<16;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum512":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<32;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum1024":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<64;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum2048":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<128;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum4096":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<256;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrum8192":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<4;i++) {
                        slotSizes.add(16*1024);
                    }
                    for(int i = 0;i<512;i++) {
                        pageSizes.add(16*1024);
                    }
                    currentSlot = 3;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "zxspectrumnext":
                    slotSizes.clear();
                    pageSizes.clear();
                    for(int i = 0;i<8;i++) {
                        slotSizes.add(8*1024);
                    }
                    for(int i = 0;i<224;i++) {
                        pageSizes.add(8*1024);
                    }
                    currentSlot = 7;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                case "noslot64k":
                    slotSizes.clear();
                    pageSizes.clear();
                    slotSizes.add(64*1024);
                    for(int i = 0;i<32;i++) {
                        pageSizes.add(64*1024);
                    }
                    currentSlot = 0;
                    currentPages.clear();
                    currentPages.add(0);
                    break;
                    
                default:
                    break;
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("=")) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode in " + sl);
                return false;
            }
            
            s.label.clearCache();
            s.label.resolveEagerly = true;                        
                        
            tokens.remove(0);
            if (!config.lineParser.parseEqu(tokens, l, sl, s, previous, source, code)) return false;
            Integer value = s.label.exp.evaluateToInteger(s, code, false, previous);
            if (value == null) {
                config.error("Cannot resolve eager variable in " + sl);
                return false;
            }
            s.label.exp = Expression.constantExpression(value, config);
            s.label.clearCache();
            
            // these variables should not be part of the source code:
            l.clear();
            return true;
        }       
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("savebin")) {
            // Just ignore ...
            while(!tokens.isEmpty()) {
                if (config.tokenizer.isSingleLineComment(tokens.get(0)) || 
                    config.tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                tokens.remove(0);
            }
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size()>=2 && (tokens.get(0).equalsIgnoreCase("display"))) {
            tokens.remove(0);
            List<Expression> exp_l = new ArrayList<>();
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            exp_l.add(exp);
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                exp = config.expressionParser.parse(tokens, s, previous, code);
                exp_l.add(exp);
            }
                        
            toPrint.add(new PrintRecord("printtext", 
                    source.getStatements().get(source.getStatements().size()-1), exp_l));
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("define")) {
            tokens.remove(0);

            // read variable name:
            String token = tokens.remove(0);
            if (!config.caseSensitiveSymbols) token = token.toLowerCase();
            
            // optionally read the expression:
            Expression exp = null;
            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {                    
                exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("parseEqu: Cannot parse line " + sl);
                    return false;
                }
                // remove unnecessary parenthesis:
                while(exp.type == Expression.EXPRESSION_PARENTHESIS) {
                    exp = exp.args.get(0);
                }                
            } else {
                exp = Expression.constantExpression(0, config);
            }

            // parse as a :=:      
            SourceConstant c = config.lineParser.newSourceConstant(token, exp, s, previous);
            if (c == null) {
                config.error("Problem defining symbol " + config.lineParser.getLabelPrefix() + token + " in " + sl);
                return false;
            }
            s.label = c;
            int res = code.addSymbol(c.name, c);
            if (res == -1) return false;
            if (res == 0) s.redefinedLabel = true;
            s.label.resolveEagerly = true;
            
            // these variables should not be part of the source code:
            l.clear();
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }                
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("opt")) {
            tokens.remove(0);
            while(!tokens.isEmpty() &&
                  (tokens.get(0).equalsIgnoreCase("push") ||
                   tokens.get(0).equalsIgnoreCase("pop") ||
                   tokens.get(0).equalsIgnoreCase("reset") ||
                   tokens.get(0).equalsIgnoreCase("listoff") ||
                   tokens.get(0).equalsIgnoreCase("liston") || 
                   tokens.get(0).equalsIgnoreCase("listall") ||
                   tokens.get(0).equalsIgnoreCase("listact") ||
                   tokens.get(0).equalsIgnoreCase("listmc"))) {
                tokens.remove(0);
            }
            
            // ignore for now ...            
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
                regpairs.add(regpair);
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
        if (functionName.equalsIgnoreCase("{")) {
            // TODO:
            return 0;
        }
        if (functionName.equalsIgnoreCase("{b")) {
            // TODO:
            return 0;
        }
        return null;
    }

    
    @Override
    public Expression translateToStandardExpression(String functionName, List<Expression> args, CodeStatement s, CodeBase code) {
        if (functionName.equalsIgnoreCase("high") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_RSHIFT,
                    Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                            args.get(0),
                            Expression.constantExpression(0xff00, Expression.RENDER_AS_16BITHEX, config), config), 
                        "(", config),
                    Expression.constantExpression(8, config), config);
        }
        if (functionName.equalsIgnoreCase("low") && args.size() == 1) {
            return Expression.operatorExpression(Expression.EXPRESSION_BITAND, 
                    args.get(0),
                    Expression.constantExpression(0x00ff, Expression.RENDER_AS_16BITHEX, config), config);
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
            return me;
        } else {
            return null;
        }
    }
    
    
    @Override
    public boolean postParseActions(CodeBase code)
    {
        for(PrintRecord pr:toPrint) {
            String accum = "";
            for(Expression exp:pr.exp_l) {
                accum += exp.evaluate(pr.previousStatement, code, true);
            }
            config.info(accum);
        }
        
        return true;     
    }
}

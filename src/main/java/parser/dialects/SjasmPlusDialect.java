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
import code.HTMLCodeStyle;
import code.OutputBinary;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import parser.LineParser;
import parser.MacroExpansion;
import parser.SourceLine;
import parser.SourceMacro;
import parser.TextMacro;
import workers.BinaryGenerator;

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
    
    
    public static class SaveCommand {
        CodeStatement s;
        String command;
        List<Expression> arguments = new ArrayList<>();
    }

    public static final String PHASE_PRE_LABEL_PREFIX = "__sjasmplus_phase_pre_";
    public static final String PHASE_POST_LABEL_PREFIX = "__sjasmplus_phase_post_";
    public static final String DEPHASE_LABEL_PREFIX = "__sjasmplus_dephase_";
    
    // To mimic sjasmplus initialization here: https://github.com/z00m128/sjasmplus/blob/master/sjasm/devices.cpp
    public static final int ZX_RAMTOP_DEFAULT = 0x5d5b;
    public static final int ZX_SYSVARS_ADR = 0x5c00;
    public static final int ZX_UDG_ADR = 0xff58;
    
    public static final int ZX_SYSVARS_DATA[] = {
	0xff, 0x00, 0x00, 0x00, 0xff, 0x00, 0x00, 0x00, 0x00, 0x14, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x01, 0x00, 0x06, 0x00, 0x0b, 0x00, 0x01, 0x00, 0x01, 0x00, 0x06, 0x00, 0x10, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x40, 0x00, 0xff, 0xcc, 0x01, 0x54, 0xff, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x00, 0x00, 0xcb, 0x5c, 0x00, 0x00, 0xb6,
	0x5c, 0xb6, 0x5c, 0xcb, 0x5c, 0x00, 0x00, 0xca, 0x5c, 0xcc, 0x5c, 0xcc, 0x5c, 0xcc, 0x5c, 0x00,
	0x00, 0xce, 0x5c, 0xce, 0x5c, 0xce, 0x5c, 0x00, 0x92, 0x5c, 0x10, 0x02, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x58, 0xff, 0x00, 0x00, 0x21,
	0x5b, 0x00, 0x21, 0x17, 0x00, 0x40, 0xe0, 0x50, 0x21, 0x18, 0x21, 0x17, 0x01, 0x38, 0x00, 0x38,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x57, 0xff, 0xff, 0xff, 0xf4, 0x09, 0xa8, 0x10, 0x4b, 0xf4, 0x09, 0xc4, 0x15, 0x53,
	0x81, 0x0f, 0xc4, 0x15, 0x52, 0xf4, 0x09, 0xc4, 0x15, 0x50, 0x80, 0x80, 0x0d, 0x80, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    public static final int ZX_STACK_DATA[] = {
	0x03, 0x13, 0x00, 0x3e
    };

    public static final int ZX_UDG_DATA[] = {
	0x00, 0x3c, 0x42, 0x42, 0x7e, 0x42, 0x42, 0x00, 0x00, 0x7c, 0x42, 0x7c, 0x42, 0x42, 0x7c, 0x00,
	0x00, 0x3c, 0x42, 0x40, 0x40, 0x42, 0x3c, 0x00, 0x00, 0x78, 0x44, 0x42, 0x42, 0x44, 0x78, 0x00,
	0x00, 0x7e, 0x40, 0x7c, 0x40, 0x40, 0x7e, 0x00, 0x00, 0x7e, 0x40, 0x7c, 0x40, 0x40, 0x40, 0x00,
	0x00, 0x3c, 0x42, 0x40, 0x4e, 0x42, 0x3c, 0x00, 0x00, 0x42, 0x42, 0x7e, 0x42, 0x42, 0x42, 0x00,
	0x00, 0x3e, 0x08, 0x08, 0x08, 0x08, 0x3e, 0x00, 0x00, 0x02, 0x02, 0x02, 0x42, 0x42, 0x3c, 0x00,
	0x00, 0x44, 0x48, 0x70, 0x48, 0x44, 0x42, 0x00, 0x00, 0x40, 0x40, 0x40, 0x40, 0x40, 0x7e, 0x00,
	0x00, 0x42, 0x66, 0x5a, 0x42, 0x42, 0x42, 0x00, 0x00, 0x42, 0x62, 0x52, 0x4a, 0x46, 0x42, 0x00,
	0x00, 0x3c, 0x42, 0x42, 0x42, 0x42, 0x3c, 0x00, 0x00, 0x7c, 0x42, 0x42, 0x7c, 0x40, 0x40, 0x00,
	0x00, 0x3c, 0x42, 0x42, 0x52, 0x4a, 0x3c, 0x00, 0x00, 0x7c, 0x42, 0x42, 0x7c, 0x44, 0x42, 0x00,
	0x00, 0x3c, 0x40, 0x3c, 0x02, 0x42, 0x3c, 0x00, 0x00, 0xfe, 0x10, 0x10, 0x10, 0x10, 0x10, 0x00,
	0x00, 0x42, 0x42, 0x42, 0x42, 0x42, 0x3c, 0x00
    };    
    
    String deviceName = null;
    int deviceRAMSize = 64*1024;
    byte deviceRAMInit[] = null;
    List<Integer> slotSizes = new ArrayList<>();
    List<Integer> pageSizes = new ArrayList<>();
    Integer currentSlot = null;
    
    List<CodeStatement> phaseStatements = new ArrayList<>();
    List<CodeStatement> dephaseStatements = new ArrayList<>();
    
    // Addresses are not resolved until the very end, so, when printing values, we just queue them up here, and
    // print them all at the very end:
    List<PrintRecord> toPrint = new ArrayList<>();
    
    // Record of "savebin", "savesna", etc. commands, to execute them after the code has been loaded:
    List<SaveCommand> saveCommands = new ArrayList<>();
    
    HashMap<String, List<Expression>> defarrays = new HashMap<>();
    
    Expression minimumOutputSize = null;
    CodeStatement minimumOutputSize_statement = null;

    SjasmPlusDialect(MDLConfig a_config) {
        config = a_config;

        config.warning_jpHlWithParenthesis = true;
        config.macrosToEvaluateEagerly.add(config.preProcessor.MACRO_IFDEF);
        config.macrosToEvaluateEagerly.add(config.preProcessor.MACRO_IFNDEF);
        
        config.bracketIncludeFilePathSearchOrder = new int[]{
                MDLConfig.FILE_SEARCH_WORKING_DIRECTORY,
                MDLConfig.FILE_SEARCH_ADDITIONAL_PATHS,
                MDLConfig.FILE_SEARCH_RELATIVE_TO_INCLUDING_FILE};
        
        config.lineParser.tokensPreventingTextMacroExpansion.add("ifdef");
        config.lineParser.tokensPreventingTextMacroExpansion.add("ifndef");
        config.lineParser.tokensPreventingTextMacroExpansion.add("define");
        config.lineParser.tokensPreventingTextMacroExpansion.add("define+");
        config.lineParser.tokensPreventingTextMacroExpansion.add("undefine");
        
        config.lineParser.addKeywordSynonym("byte", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("word", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("dword", config.lineParser.KEYWORD_DD);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym("block", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym("::", config.lineParser.KEYWORD_COLON);
                
        config.lineParser.keywordsHintingANonScopedLabel.add("=");
        config.lineParser.keywordsHintingANonScopedLabel.add("defl");
                
        config.warning_jpHlWithParenthesis = true;
        config.lineParser.allowEmptyDB_DW_DD_definitions = true;
        config.lineParser.allowIncludesWithoutQuotes = true;
        config.lineParser.macroDefinitionStyle = LineParser.MACRO_BOTH;
        config.lineParser.allowNumberLabels = true;
        config.lineParser.allowColonSeparatedInstructions = true;
        config.lineParser.applyEscapeSequencesToIncludeArguments = false;
        
        config.opParser.allowExtendedSjasmplusLDInstructions = true;
        
        config.expressionParser.addRegisterSynonym("xl", "ixl");
        config.expressionParser.addRegisterSynonym("lx", "ixl");
        config.expressionParser.addRegisterSynonym("xh", "ixh");
        config.expressionParser.addRegisterSynonym("hx", "ixh");
        config.expressionParser.addRegisterSynonym("yl", "iyl");
        config.expressionParser.addRegisterSynonym("ly", "iyl");
        config.expressionParser.addRegisterSynonym("yh", "iyh");
        config.expressionParser.addRegisterSynonym("hy", "iyh");
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "high", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "low", config.expressionParser.OPERATOR_PRECEDENCE[Expression.EXPRESSION_SUM]);
        config.expressionParser.dialectFunctionsSingleArgumentNoParenthesisPrecedence.put(
                "$$", 0);
        config.expressionParser.sjasmPlusCurlyBracketExpressions = true;
        config.expressionParser.allowArrayIndexingSyntax = true;
        config.expressionParser.arrayIndexSignifyingLength = "#";
                        
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
        config.tokenizer.multilineCommentStartTokens.remove("{");
        config.tokenizer.multilineCommentEndTokens.remove("}");
        config.tokenizer.allowDotFollowedByNumberLabels = false;
        config.tokenizer.numericConstantsCanContainQoutes = true;
        config.tokenizer.additionalNonFirstSymbolCharacters = new ArrayList<>();
        config.tokenizer.additionalNonFirstSymbolCharacters.add("!");
        config.tokenizer.additionalNonFirstSymbolCharacters.add("?");
        config.tokenizer.additionalNonFirstSymbolCharacters.add("#");        
    }
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens, SourceConstant label, CodeBase code) {
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("struct")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("ends")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("end")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("assert")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("outend")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("size")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("page")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("dz")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("align")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("module")) return true;
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("endmodule")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("device")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("=")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("defl")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("savebin")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("savesna")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("display")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("define")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("define+")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("undefine")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("abyte")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("opt")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("phase")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("dephase")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("disp")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase("ent")) return true;
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".") &&
                (tokens.get(1).equalsIgnoreCase("(") || config.tokenizer.isInteger(tokens.get(1)))) {
            return true;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("defarray")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("mmu")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("savenex")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("cspectmap")) return true;
        

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
            if (!endStructDefinition(sl, source, code)) return false;
            
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
                config.error("Assertion ("+exp+") failed in " + sl);
                return false;
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }

        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("output")) {
            // ignore for now ...
            while(!tokens.isEmpty()) {
                if (config.tokenizer.isSingleLineComment(tokens.get(0)) || 
                    config.tokenizer.isMultiLineCommentStart(tokens.get(0))) break;
                tokens.remove(0);
            }

            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("size")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse size argument in " + sl);
                return false;
            }
            minimumOutputSize = exp;
            minimumOutputSize_statement = s;
            linesToKeepIfGeneratingDialectAsm.add(s);
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
            deviceName = tokens.remove(0).toLowerCase();
            int ramtop = 0x5d5b;
            
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
                    initZXRam(0x4000, 4, ramtop);
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
                    initZXRam(0x4000, 8, ramtop);
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
                    initZXRam(0x4000, 16, ramtop);
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
                    initZXRam(0x4000, 32, ramtop);
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
                    initZXRam(0x4000, 64, ramtop);
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
                    initZXRam(0x4000, 128, ramtop);
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
                    initZXRam(0x4000, 256, ramtop);
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
                    initZXRam(0x4000, 512, ramtop);
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
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && (tokens.get(0).equalsIgnoreCase("=") ||
                                   tokens.get(0).equalsIgnoreCase("defl"))) {
            // This is like an equ, but with a variable that changes value throughout parsing.
            // This only makes sense in eager execution, so, we check for that:
            if (!config.eagerMacroEvaluation) {
                config.error("Non final variable defined in lazy evaluation mode in " + sl);
                return false;
            }
            
            s.label.clearCache();
            s.label.resolveEagerly = true;                        
                        
            tokens.remove(0);
            
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("parse =: Cannot parse line " + sl);
                return false;
            }
            // remove unnecessary parenthesis:
            while(exp.type == Expression.EXPRESSION_PARENTHESIS) {
                exp = exp.args.get(0);
            }
            s.type = CodeStatement.STATEMENT_CONSTANT;
            Integer value = exp.evaluateToInteger(s, code, false, previous);
            if (value == null) {
                config.error("Cannot resolve expression "+ exp+" for eager variable in " + sl);
                return false;
            }
            s.label.exp = Expression.constantExpression(value, config);
            s.label.clearCache();
            
            // these variables should not be part of the source code:
            l.clear();
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }       
        if (tokens.size() >= 2 && (tokens.get(0).equalsIgnoreCase("savebin") ||
                                   tokens.get(0).equalsIgnoreCase("savesna"))) {
            SaveCommand sc = new SaveCommand();
            sc.s = s;
            sc.command = tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse line " + sl);
                return false;
            }
            sc.arguments.add(exp);
            while(!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
                exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("Cannot parse line " + sl);
                    return false;
                }
                sc.arguments.add(exp);
            }
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            saveCommands.add(sc);
            
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
        if (tokens.size() >= 2 && 
                (tokens.get(0).equalsIgnoreCase("define") ||
                 tokens.get(0).equalsIgnoreCase("define+"))) {
            String keyword = tokens.remove(0);

            // Text macros:

            // read variable name:
            String macroName = tokens.remove(0);
            if (!config.caseSensitiveSymbols) macroName = macroName.toLowerCase();
            
            List<String> macroTokens = new ArrayList<>();
                    
            // parameters parsed, parse the body:
            for(String token:tokens) {
                if (config.tokenizer.isSingleLineComment(token)) break;
                macroTokens.add(token);
            }
            tokens.clear();

            TextMacro macro = new TextMacro(macroName, new ArrayList<>(), macroTokens, s);
            if (keyword.equalsIgnoreCase("define")) {
                // check if it's a duplicate, and complain:
                if (config.preProcessor.getTextMacro(macroName, 0) != null) {
                    config.error("Redefining " + macroName + " in " + sl);
                    config.error("Use 'define+' if you want to change its value half way through assembling.");
                    return false;
                }
            }
            config.preProcessor.addTextMacro(macro);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase("undefine")) {
            tokens.remove(0);
            String macroName = tokens.remove(0);
            TextMacro macro = config.preProcessor.getTextMacro(macroName, 0);
            if (macro != null) {
                config.preProcessor.removeTextMacro(macroName, 0);
            }
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }      
        if (tokens.size() >= 1 && (tokens.get(0).equalsIgnoreCase("disp") || tokens.get(0).equalsIgnoreCase("phase"))) {
            tokens.remove(0);
            
            if (s.label != null) {
                // if there was a label in the "phase" line, create a new one:
                s = new CodeStatement(CodeStatement.STATEMENT_ORG, new SourceLine(sl), source, config);
                l.add(s);
            }
            
            // parse as an "org":
            Expression exp = config.expressionParser.parse(tokens, s, previous, code);
            if (exp == null) {
                config.error("Cannot parse phase address in " + sl);
                return false;
            } 
            phaseStatements.add(s);
            linesToKeepIfGeneratingDialectAsm.add(s);
            
            // Add the label before the org:
            String phase_pre_label_name = PHASE_PRE_LABEL_PREFIX + phaseStatements.size();
            s.type = CodeStatement.STATEMENT_ORG;
            s.org = exp;
            s.label = new SourceConstant(phase_pre_label_name, phase_pre_label_name, 
                                         Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config),
                                         s, config);
            code.addSymbol(phase_pre_label_name, s.label);
            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s);
            
            // Add the label after the org:
            s = new CodeStatement(CodeStatement.STATEMENT_NONE, new SourceLine(sl), source, config);
            l.add(s);
            String phase_post_label_name = PHASE_POST_LABEL_PREFIX + phaseStatements.size();
            s.label = new SourceConstant(phase_post_label_name, phase_post_label_name, 
                                         Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config),
                                         s, config);
            code.addSymbol(phase_post_label_name, s.label);
            auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s);
            
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 1 && (tokens.get(0).equalsIgnoreCase("ent") || tokens.get(0).equalsIgnoreCase("dephase"))) {
            tokens.remove(0);
            
            if (s.label != null) {
                // if there was a label in the "phase" line, create a new one:
                s = new CodeStatement(CodeStatement.STATEMENT_ORG, sl, source, config);
                l.add(s);
                auxiliaryStatementsToRemoveIfGeneratingDialectasm.add(s);
            }            

            // restore normal mode addressing:
            String phase_pre_label_name = PHASE_PRE_LABEL_PREFIX + phaseStatements.size();
            String phase_post_label_name = PHASE_POST_LABEL_PREFIX + phaseStatements.size();
            String dephase_label_name = DEPHASE_LABEL_PREFIX + phaseStatements.size();

            // __asmsx_phase_pre_* + (__asmsx_dephase_* - __asmsx_phase_post_*)
            Expression exp = Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                    Expression.symbolExpression(phase_pre_label_name, s, code, config),
                    Expression.parenthesisExpression(
                            Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                    Expression.symbolExpression(dephase_label_name, s, code, config),
                                    Expression.symbolExpression(phase_post_label_name, s, code, config), config), 
                            "(", config), config);
            
            s.type = CodeStatement.STATEMENT_ORG;
            s.org = exp;
            s.label = new SourceConstant(dephase_label_name, dephase_label_name, 
                                         Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, s, code, config),
                                         s, config);
            code.addSymbol(dephase_label_name, s.label);
            dephaseStatements.add(s);
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }        
        if (tokens.size() >= 1 && tokens.get(0).equalsIgnoreCase("outend")) {
            tokens.remove(0);
            // ignore for now ...            
            linesToKeepIfGeneratingDialectAsm.add(s);
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
                   tokens.get(0).equalsIgnoreCase("listmc") ||
                   tokens.get(0).equalsIgnoreCase("--") ||
                   tokens.get(0).equalsIgnoreCase("=") ||
                   tokens.get(0).equalsIgnoreCase("syntax") ||
                   tokens.get(0).equalsIgnoreCase("abfw") ||
                   tokens.get(0).equalsIgnoreCase("zxnext") ||
                   tokens.get(0).equalsIgnoreCase("cspect") ||
                   tokens.get(0).equalsIgnoreCase("abf"))) {
                tokens.remove(0);
            }
            
            // ignore for now ...            
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);
        }
        if (tokens.size() >= 2 && tokens.get(0).equalsIgnoreCase(".") &&
                (tokens.get(1).equalsIgnoreCase("(") || config.tokenizer.isInteger(tokens.get(1)))) {
            tokens.remove(0);
            Expression numberExp = config.expressionParser.parse(tokens, s, previous, code);
            Integer number = numberExp.evaluateToInteger(s, code, false);
            if (number == null) {
                config.error("Cannot evaluate " + numberExp + " to an integer in " + sl);
                return false;
            }
            l.clear();
            for(int i = 0;i<number;i++) {
                List<String> tokensCopy = new ArrayList<>();
                tokensCopy.addAll(tokens);
                // we need to parse it every time, to create multiple different copies of the statements:
                List<CodeStatement> l2 = config.lineParser.parse(tokensCopy, sl, source, previous, code, config);
                if (l2 == null) {
                    config.error("Cannot parse line in " + sl);
                    return false;
                }
                l.addAll(l2);
            }
            return true;
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("defarray")) {
            tokens.remove(0);
            String arrayName = tokens.remove(0);
            List<Expression> data = new ArrayList<>();
            boolean done = false;        
            while (!done) {
                Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                if (exp == null) {
                    config.error("parseData: Cannot parse line " + sl);
                    return false;
                } else {
                    data.add(exp);
                }
                if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                    tokens.remove(0);
                } else {
                    done = true;
                }
            }
            
            defarrays.put(arrayName, data);
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);            
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("mmu")) {
            // ignore for now:
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                tokens.remove(0);
            }
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);            
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("savenex")) {
            // ignore for now:
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                tokens.remove(0);
            }
            
            linesToKeepIfGeneratingDialectAsm.add(s);
            return config.lineParser.parseRestofTheLine(tokens, l, sl, s, previous, source, code);            
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("cspectmap")) {
            // ignore for now:
            while(!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                tokens.remove(0);
            }
            
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
        
        // multiargument instructions:
        if (tokens.size()>=4 && tokens.get(0).equalsIgnoreCase("cp")) {
            // instructions of the style: cp d,8, which translate to "cp d", "cp 8" (useless, but still, I've found it in some projects...)
            boolean process = true;
            List<Expression> args = new ArrayList<>();
            List<String> tokens2 = new ArrayList<>();
            tokens2.addAll(tokens);
            tokens2.remove(0);
            int tokenstoRemoveStart = -1;
            while(true) {
                Expression exp = config.expressionParser.parse(tokens2, s, previous, code);
                if (exp == null) {
                    process = false;
                    break;
                }
                args.add(exp);
                if (tokens2.isEmpty() || !tokens2.get(0).equals(",")) break;
                if (tokenstoRemoveStart == -1) {
                    tokenstoRemoveStart = tokens.size() - tokens2.size();
                }
                tokens2.remove(0);
            }
            if (process && args.size() >= 2) {
                int tokensToRemoveEnd = tokens.size() - tokens2.size();
                for(int i = 0;i < tokensToRemoveEnd-tokenstoRemoveStart;i++) {
                    tokens.remove(tokenstoRemoveStart);
                }
                args.remove(0); // the first arg will be processed later by the regular MDL parser
                for(Expression arg:args) {
                    CodeStatement auxiliaryS = new CodeStatement(CodeStatement.STATEMENT_CPUOP, sl, source, config);
                    List<Expression> auxiliaryArguments = new ArrayList<>();
                    auxiliaryArguments.add(arg);
                    List<CPUOp> op_l = config.opParser.parseOp("cp", auxiliaryArguments, s, previous, code);
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
        if (functionName.equalsIgnoreCase("$$")) {
            if (args.isEmpty()) {
                if (currentPages.isEmpty()) {
                    config.error("Canot evaluate '"+functionName+"' as we have no current page defined, in " + s.sl);
                    return null;
                } else {
                    return currentPages.get(0);
                }
            } else if (args.size() == 1) {
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
        }
        if (functionName.equalsIgnoreCase("{") && args.size() == 1) {
            byte []memory = reconstructDeviceRAMUntil(null, code);
            if (memory == null) {
                config.error("Cannot reconstruct device memory in " + s.sl);
                return null;
            }
            Integer address = args.get(0).evaluateToInteger(s, code, silent);
            if (address == null) {
                config.error("Cannot evaluate " + args.get(0) + " ro an integer in " + s.sl);
                return null;
            }
            int val = memory[address % deviceRAMSize] + 256 * memory[(address+1) % deviceRAMSize];
            return val;
        }
        if (functionName.equalsIgnoreCase("{b") && args.size() == 1) {
            byte []memory = reconstructDeviceRAMUntil(null, code);
            if (memory == null) {
                config.error("Cannot reconstruct device memory in " + s.sl);
                return null;
            }
            Integer address = args.get(0).evaluateToInteger(s, code, silent);
            if (address == null) {
                config.error("Cannot evaluate " + args.get(0) + " ro an integer in " + s.sl);
                return null;
            }
            int val = memory[address % deviceRAMSize];
            return val;
        }
        if (functionName.equalsIgnoreCase("array.length") && args.size() == 1 &&
            args.get(0).isConstant() &&
            args.get(0).stringConstant != null) {
            String arrayName = args.get(0).stringConstant;
            List<Expression> array = defarrays.get(arrayName);
            if (array == null) {
                config.error("Requesting the length of an undefined array " + arrayName + " in " + s.sl);
                return null;
            }
            return array.size();
        }
        if (functionName.equalsIgnoreCase("array.index") && args.size() == 2 &&
            args.get(0).isConstant() &&
            args.get(0).stringConstant != null) {
            String arrayName = args.get(0).stringConstant;
            List<Expression> array = defarrays.get(arrayName);
            if (array == null) {
                config.error("Indexing an undefined array " + arrayName + " in " + s.sl);
                return null;
            }
            Integer index = args.get(1).evaluateToInteger(s, code, silent);
            if (index == null) {
                config.error("Cannot evaluate array index in " + s.sl);
                return null;
            }
            return array.get(index).evaluateToInteger(s, code, silent);
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
        if (functionName.equalsIgnoreCase("array.length") && args.size() == 1 &&
            args.get(0).isConstant() &&
            args.get(0).stringConstant != null) {
            String arrayName = args.get(0).stringConstant;
            List<Expression> array = defarrays.get(arrayName);
            if (array == null) {
                config.error("Requesting the length of an undefined array " + arrayName + " in " + s.sl);
                return null;
            }
            return Expression.constantExpression(array.size(), config);
        }
        if (functionName.equalsIgnoreCase("array.index") && args.size() == 2 &&
            args.get(0).isConstant() &&
            args.get(0).stringConstant != null) {
            String arrayName = args.get(0).stringConstant;
            List<Expression> array = defarrays.get(arrayName);
            if (array == null) {
                config.error("Indexing an undefined array " + arrayName + " in " + s.sl);
                return null;
            }
            Integer index = args.get(1).evaluateToInteger(s, code, false);
            if (index == null) {
                config.error("Cannot evaluate array index in " + s.sl);
                return null;
            }
            return array.get(index);
        } 
        return null;
    }
    
    
    @Override
    public boolean expressionEvaluatesToIntegerConstant(String functionName) {
        if (functionName.equalsIgnoreCase("{") || 
            functionName.equalsIgnoreCase("{b") ||
            functionName.equalsIgnoreCase("$$")) {
            return true;
        }
        return false;
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
        if (minimumOutputSize != null) {
            if (!minimumOutputSize.evaluatesToIntegerConstant()) {
                config.error("Cannot evaluate output binary size expression to integer: " + minimumOutputSize);
                return false;
            }
            Integer size = minimumOutputSize.evaluateToInteger(minimumOutputSize_statement, code, true);
            if (size == null) {
                config.error("Cannot evaluate output binary size expression to integer: " + minimumOutputSize);
                return false;
            }
            code.outputs.get(0).minimumSize = size;
        }
        
        for(PrintRecord pr:toPrint) {
            String accum = "";
            for(Expression exp:pr.exp_l) {
                accum += exp.evaluate(pr.previousStatement, code, true);
            }
            config.info(accum);
        }
        
        // Saving binaries/snapshots, etc.:
        for(SaveCommand sc:saveCommands) {
            code.resetAddresses();

            String fileName = sc.arguments.get(0).evaluateToString(sc.s, code, true);
            if (fileName == null) {
                config.error("Cannot evaluate file name in " + sc.s.sl);
                return false;
            }
            
            switch(sc.command) {
                case "savebin":
                {
                    Integer start = 0;
                    Integer length = deviceRAMSize;
                    if (sc.arguments.size() >= 2) {
                        start = sc.arguments.get(1).evaluateToInteger(sc.s, code, false);
                        if (start == null) {
                            config.error("Cannot evaluate start address in " + sc.s.sl);
                            return false;
                        }
                    }
                    if (sc.arguments.size() >= 3) {
                        length = sc.arguments.get(2).evaluateToInteger(sc.s, code, false);
                        if (length == null) {
                            config.error("Cannot evaluate length in " + sc.s.sl);
                            return false;
                        }
                    }
                    
                    byte device[] = reconstructDeviceRAMUntil(sc.s, code);
                    if (device == null) return false;

                    try (FileOutputStream os = new FileOutputStream(fileName)) {
                        start = start%deviceRAMSize;
                        if (start + length > deviceRAMSize) {
                            length = deviceRAMSize - start;
                        }
                        os.write(device, start, length);
                        os.flush();
                    } catch (Exception e) {
                        config.error("Cannot write to file " + fileName + ": " + e);
                        config.error(Arrays.toString(e.getStackTrace()));
                        return false;
                    }

                    break;
                }
                case "savesna":
                {
                    Integer start = 0;
                    if (sc.arguments.size() >= 2) {
                        start = sc.arguments.get(1).evaluateToInteger(sc.s, code, false);
                        if (start == null) {
                            config.error("Cannot evaluate start address in " + sc.s.sl);
                            return false;
                        }
                    }
                    
                    if (deviceName == null ||
                        (!deviceName.equalsIgnoreCase("ZXSPECTRUM48") &&
                         !deviceName.equalsIgnoreCase("ZXSPECTRUM128"))) {
                        config.error("savesna can only be used on ZXSPECTRUM48 or ZXSPECTRUM128 devices in " + sc.s.sl);
                        return false;                        
                    }
                    
                    byte device[] = reconstructDeviceRAMUntil(sc.s, code);
                    if (device == null) return false;
                    
                    // Values/procedure from the sourcecode of sjasmplus: https://github.com/z00m128/sjasmplus/blob/master/sjasm/io_snapshots.cpp
                    // For more information on the .sna format: https://sinclair.wiki.zxnet.co.uk/wiki/SNA_format
                    byte []snaHeader = {0x3f, 0x58, 0x27, (byte)0x9b, 0x36, 0x00, 0x00, 0x44,
                                        0x00, 0x2b, 0x2d, (byte)0xdc, 0x5c, (byte)(start & 0xff), (byte)((start>>8)&0xff), 0x3a,
                                        0x5c, 0x3c, (byte)0xff, 0x00, 0x00, 0x54, 0x00,
                                        0x00, 0x00, // these will be modified below (stackAdr)
                                        0x01, 0x07
                            };
                    int ZX_STACK_DATA_size = 4;
                    int ZX_RAMTOP_DEFAULT = 0x5d5b;
                    int stackAdr = ZX_RAMTOP_DEFAULT + 1 - ZX_STACK_DATA_size;
                    boolean is48kSnap = deviceName.equalsIgnoreCase("ZXSPECTRUM48");

                    // Assuming we have a "defaultStack":
                    if (is48kSnap) stackAdr -= 2;
                    snaHeader[23] = (byte)(stackAdr & 0xff);
                    snaHeader[24] = (byte)((stackAdr>>8) & 0xff);
                    if (is48kSnap) {
                        // inject PC under default stack
                        device[stackAdr] = (byte)(start & 0xff);
                        device[stackAdr+1] = (byte)((start>>8) & 0xff);
                    }
                    
                    try (FileOutputStream os = new FileOutputStream(fileName)) {
                        os.write(snaHeader, 0, snaHeader.length);
                        start = 16*1024;
                        os.write(device, start, 48*1024);
                        os.flush();
                    } catch (Exception e) {
                        config.error("Cannot write to file " + fileName + ": " + e);
                        config.error(Arrays.toString(e.getStackTrace()));
                        return false;
                    }                    
                    break;
                }
                default:
                    config.error("Unsupported save command " + sc.command);
                    return false;
            }
        }
        
        return true;     
    }
    
    
    // Code to mimic sjasmplus' behavior here: https://github.com/z00m128/sjasmplus/blob/master/sjasm/devices.cpp
    void initZXRam(int slotSize, int slotCount, int ramtop)
    {
        deviceRAMSize = 64*1024;
        deviceRAMInit = new byte[deviceRAMSize];
//	device->ZxRamTop = (0x5D00 <= ramtop && ramtop <= 0xFFFF) ? ramtop : ZX_RAMTOP_DEFAULT;

	// ULA attributes: INK 0 : PAPER 7 : FLASH 0 : BRIGTH 0
	for (int adr = 0x5800; adr < 0x5B00; adr++) {
            deviceRAMInit[adr] = 7*8;
        }

	// set UDG data at ZX_UDG_ADR (0xFF58)
        for(int i = 0;i<ZX_UDG_DATA.length;i++) {
            deviceRAMInit[ZX_UDG_ADR+i] = (byte)ZX_UDG_DATA[i];
        }

	// ZX SYSVARS at 0x5C00
        for(int i = 0;i<ZX_SYSVARS_DATA.length;i++) {
            deviceRAMInit[ZX_SYSVARS_ADR+i] = (byte)ZX_SYSVARS_DATA[i];
        }

	// set RAMTOP sysvar
        deviceRAMInit[ZX_SYSVARS_ADR+0xb2] = (byte)(ramtop & 0xff);
        deviceRAMInit[ZX_SYSVARS_ADR+0xb3] = (byte)((ramtop >> 8) & 0xff);

	// set STACK data (without the "start" address)
	int adr = ramtop + 1 - ZX_STACK_DATA.length;
	// set ERRSP sysvar to beginning of the fake stack
        deviceRAMInit[ZX_SYSVARS_ADR+0x3d] = (byte)(adr & 0xff);
        deviceRAMInit[ZX_SYSVARS_ADR+0x3e] = (byte)((adr >> 8) & 0xff);
	for (int value : ZX_STACK_DATA) {
            deviceRAMInit[adr] = (byte)value;
            adr++;
        }
    }
    
    
    public byte[] reconstructDeviceRAMUntil(CodeStatement limit, CodeBase code)
    {
        byte memory[] = new byte[deviceRAMSize];
        BinaryGenerator bingen = new BinaryGenerator(config);
        
        if (deviceRAMInit != null) {
            for(int i = 0;i<deviceRAMSize;i++) memory[i] = deviceRAMInit[i];
        }
        
        // iterate over all the code statements, generating binary data, and overwritting the memory
        for(OutputBinary output:code.outputs) {
            if (output.main == null) continue;
            
            Boolean ret = reconstructDeviceRAMUntil(output.main, limit, code, memory, bingen);
            if (ret == null) return null;
            if (ret) {
                // we found the limit:
                return memory;
            }
        }
        
        return memory;
    }


    // true: limit found
    // false: limit not found
    // null: error
    public Boolean reconstructDeviceRAMUntil(SourceFile f, CodeStatement limit, CodeBase code, byte memory[], BinaryGenerator bingen)
    {
        int phaseOffset = 0;
        for(CodeStatement s:f.getStatements()) {
            if (s == limit) return true;
            Integer address = s.getAddress(code);
            if (address == null) {
                config.error("Cannot assess the address of statement in " + s);
                return null;
            }
            if (s.type == CodeStatement.STATEMENT_ORG && s.label != null) {
                if (s.label.name.startsWith(PHASE_PRE_LABEL_PREFIX)) {
                    // this is a "phase" org, the address after should be ignored!
                    Integer org = s.org.evaluateToInteger(s, code, true);
                    if (org == null) {
                        config.error("Cannot evaluate " +s.org+ " to an integer in " + s);
                        return null;
                    }
                    phaseOffset = address - org;
                } else if (s.label.name.startsWith(DEPHASE_LABEL_PREFIX)) {
                    phaseOffset = 0;
                }                
            }
            address += phaseOffset;
            if (address < 0 || address >= deviceRAMSize) {
                config.warn("Address ("+address+") ouside of device RAM range ("+deviceRAMSize+") in " + s);
            }
            if (s.type == CodeStatement.STATEMENT_INCLUDE) {
                Boolean ret = reconstructDeviceRAMUntil(s.include, limit, code, memory, bingen);
                if (ret == null || ret) return ret;
            } else {
                byte data[] = bingen.generateStatementBytes(s, code);
                if (data != null) {
                    for(int i = 0;i<data.length;i++) {
                        memory[(address+i)%deviceRAMSize] = data[i];
                    }
                }
            }
        }
        
        return false;
    }
    
    
    @Override
    public String statementToString(CodeStatement s, CodeBase code, Path rootPath, HTMLCodeStyle style) {
        boolean useOriginalNames = false;
        if (linesToKeepIfGeneratingDialectAsm.contains(s)) {
            return s.sl.line;
        }

        if (auxiliaryStatementsToRemoveIfGeneratingDialectasm.contains(s)) return "";
        
        return s.toStringUsingRootPath(rootPath, useOriginalNames, true, code, style);
    }        
}

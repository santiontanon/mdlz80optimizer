;--------------------------------------------------------
; File Created by SDCC : free open source ANSI-C Compiler
; Version 3.9.0 #11195 (Mac OS X x86_64)
;--------------------------------------------------------
    .allow_undocumented
	.module graphics
	.optsdcc -mz80
;--------------------------------------------------------
; Public variables in this module
;--------------------------------------------------------
	.globl _TMS99X8_memset
	.globl _TMS99X8_memcpy128
	.globl _TMS99X8_memcpy64
	.globl _TMS99X8_memcpy32
	.globl _TMS99X8_memcpy16
	.globl _TMS99X8_memcpy8
	.globl _TMS99X8_memcpy_slow
	.globl _TMS99X8_activateMode2
	.globl _textProperties
	.globl _SA
	.globl _TMS99X8_status
	.globl _TMS99X8
	.globl _initCanvas
	.globl _undoPoint
	.globl _setROI
	.globl _setPoint
	.globl _setPoints_v8
	.globl _setPoints_v8c
	.globl _getPoints_v8
	.globl _setHistoricPoint
	.globl _restoreHistoricPoint
	.globl _setPointFG
	.globl _setPointBG
	.globl _rectangle
	.globl _rectangleColor
	.globl _setHLine
	.globl _writeText
	.globl _lineFast
;--------------------------------------------------------
; special function registers
;--------------------------------------------------------
_VDP0 .equ 0x0098
_VDP1 .equ 0x0099
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
	.area _DATA
_TMS99X8 .equ 0xf3df
_TMS99X8_status .equ 0xf3e7
_SA::
	.ds 128
_history_location:
	.ds 512
_history_value:
	.ds 256
_history_shift:
	.ds 256
_history_pt:
	.ds 1
_ROI_x0:
	.ds 1
_ROI_x1:
	.ds 1
_ROI_y0:
	.ds 1
_ROI_y1:
	.ds 1
_textProperties::
	.ds 20
_screen_copy .equ 0xd000
_offset_x .equ 0xe800
_offset_y .equ 0xea00
_shift8 .equ 0xec00
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
	.area _INITIALIZED
;--------------------------------------------------------
; absolute external ram data
;--------------------------------------------------------
	.area _DABS (ABS)
;--------------------------------------------------------
; global & static initialisations
;--------------------------------------------------------
	.area _HOME
	.area _GSINIT
	.area _GSFINAL
	.area _GSINIT
;--------------------------------------------------------
; Home
;--------------------------------------------------------
	.area _HOME
	.area _HOME
;--------------------------------------------------------
; code
;--------------------------------------------------------
	.area _CODE
;src//graphics/graphics.c:89: void initCanvas() {
;	---------------------------------
; Function initCanvas
; ---------------------------------
_initCanvas::
	push ix
	ld ix, #0
	add ix, sp
	ld hl, #-7
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:94: TMS99X8_activateMode2(MODE2_ALL_ROWS); 
	ld a, #0x07
	push af
	inc sp
	call _TMS99X8_activateMode2
	inc sp
;src//graphics/graphics.c:97: for (uint8_t n=0; n<32; n++) { SA[n].y = 209; }
	ld c, #0x00
00216$:
	ld a, c
	sub #0x20
	jr nc, 00101$
	ld l, c
	ld h, #0x00
	add hl, hl
	add hl, hl
	ld de, #_SA
	add hl, de
	ld (hl), #0xd1
	inc c
	jr 00216$
00101$:
;src//graphics/graphics.c:98: TMS99X8_writeSpriteAttributes(0,SA);
;sdcc_msx/inc/tms99X8.h:294: TMS99X8_memcpy(MODE2_ADDRESS_SA0, (const uint8_t *)sa, sizeof(T_SA)); 
;sdcc_msx/inc/tms99X8.h:217: VDP1 = dst & 0xFF; 
	ld a, #0x00
	out (#_VDP1), a
;sdcc_msx/inc/tms99X8.h:218: VDP1 = 0x40 | (dst>>8);
	ld a, #0x1f
	or #0x40
	out (#_VDP1), a
;sdcc_msx/inc/tms99X8.h:273: else if (size == 128) { TMS99X8_setPtr(dst); TMS99X8_memcpy128(src); }
	ld hl, #_SA
	call _TMS99X8_memcpy128
;src//graphics/graphics.c:101: TMS99X8.sprites16 = true;
	ld hl, #(_TMS99X8 + 0x0001)
	set 1, (hl)
;sdcc_msx/inc/tms99X8.h:188: register uint8_t *r = (uint8_t *)TMS99X8;
	ld bc, #_TMS99X8
;sdcc_msx/inc/tms99X8.h:189: VDP1 = *r++;
	ld a, (bc)
	out (#_VDP1), a
	inc bc
;sdcc_msx/inc/tms99X8.h:190: VDP1 = 0x80 | 0;
	ld a, #0x80
	out (#_VDP1), a
;sdcc_msx/inc/tms99X8.h:191: VDP1 = *r++;
	ld a, (bc)
	out (#_VDP1), a
;sdcc_msx/inc/tms99X8.h:192: VDP1 = 0x80 | 1;
	ld a, #0x81
	out (#_VDP1), a
;src//graphics/graphics.c:107: do {
	ld -1 (ix), #0x00
00102$:
;src//graphics/graphics.c:108: scratchpad[((i<<5)&0xFF) + (i>>3)] = i;
	ld l, -1 (ix)
	ld h, #0x00
	add hl, hl
	add hl, hl
	add hl, hl
	add hl, hl
	add hl, hl
	ld -7 (ix), l
	ld -6 (ix), #0x00
	ld a, -1 (ix)
	rrca
	rrca
	rrca
	and #0x1f
	ld -5 (ix), a
	ld -4 (ix), #0x00
	ld a, -7 (ix)
	add a, -5 (ix)
	ld -3 (ix), a
	ld a, -6 (ix)
	adc a, -4 (ix)
	ld -2 (ix), a
	ld a, #<(_scratchpad)
	add a, -3 (ix)
	ld -5 (ix), a
	ld a, #>(_scratchpad)
	adc a, -2 (ix)
	ld -4 (ix), a
	pop bc
	pop hl
	push hl
	push bc
	ld a, -1 (ix)
	ld (hl), a
;src//graphics/graphics.c:109: } while (++i != 0);
	inc -1 (ix)
	ld a, -1 (ix)
	or a
	jr nz, 00102$
;src//graphics/graphics.c:112: TMS99X8_memcpy(MODE2_ADDRESS_PN0 + 0x0000, (const uint8_t *)scratchpad, 256);    
;sdcc_msx/inc/tms99X8.h:274: else TMS99X8_memcpy_slow(dst,src,size);
	ld hl, #0x0100
	push hl
	ld hl, #_scratchpad
	push hl
	ld hl, #0x1800
	push hl
	call _TMS99X8_memcpy_slow
	ld hl, #6
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:113: TMS99X8_memcpy(MODE2_ADDRESS_PN0 + 0x0100, (const uint8_t *)scratchpad, 256);    
;sdcc_msx/inc/tms99X8.h:274: else TMS99X8_memcpy_slow(dst,src,size);
	ld hl, #0x0100
	push hl
	ld hl, #_scratchpad
	push hl
	ld hl, #0x1900
	push hl
	call _TMS99X8_memcpy_slow
	ld hl, #6
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:114: TMS99X8_memcpy(MODE2_ADDRESS_PN0 + 0x0200, (const uint8_t *)scratchpad, 256);    
;sdcc_msx/inc/tms99X8.h:274: else TMS99X8_memcpy_slow(dst,src,size);
	ld hl, #0x0100
	push hl
	ld hl, #_scratchpad
	push hl
	ld hl, #0x1a00
	push hl
	call _TMS99X8_memcpy_slow
	ld hl, #6
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:116: TMS99X8_memset(MODE2_ADDRESS_PG, 0, sizeof(T_PG));
	ld hl, #0x1800
	push hl
	xor a
	push af
	inc sp
	ld h, #0x00
	push hl
	call _TMS99X8_memset
	pop af
	pop af
	inc sp
;src//graphics/graphics.c:117: memset(screen_copy,0,sizeof(screen_copy));
	ld hl, #_screen_copy
	ld (hl), #0x00
	ld e, l
	ld d, h
	inc de
	ld bc, #0x17ff
	ldir
;src//graphics/graphics.c:119: memcpy(offset_x, Coffset_x, sizeof(offset_x));
	ld de, #_offset_x
	ld hl, #_Coffset_x
	ld bc, #0x0200
	ldir
;src//graphics/graphics.c:120: memcpy(offset_y, Coffset_y, sizeof(offset_y));
	ld de, #_offset_y
	ld hl, #_Coffset_y
	ld bc, #0x0200
	ldir
;src//graphics/graphics.c:121: memcpy(shift8, Cshift8, sizeof(shift8));
	ld de, #_shift8
	ld hl, #_Cshift8
	ld bc, #0x0100
	ldir
;src//graphics/graphics.c:123: TMS99X8_memset(MODE2_ADDRESS_CT, FWhite + BTransparent, sizeof(T_CT));
	ld hl, #0x1800
	push hl
	ld a, #0xf0
	push af
	inc sp
	ld h, #0x20
	push hl
	call _TMS99X8_memset
	pop af
	pop af
	inc sp
;src//graphics/graphics.c:125: history_pt = 0;
	ld hl, #(_history_pt + 0)
	ld (hl), #0x00
;src//graphics/graphics.c:127: memset(&textProperties,0,sizeof(textProperties));
	ld bc, #(_textProperties + 0)
	ld l, c
	ld h, b
	push bc
	ld b, #0x0a
00268$:
	xor a
	ld (hl), a
	inc hl
	ld (hl), a
	inc hl
	djnz 00268$
	pop bc
;src//graphics/graphics.c:129: textProperties.font_segment = MODULE_SEGMENT(font_tiny,PAGE_D);
	ld a, #<(_MAPPER_MODULE_font_tiny_PAGE_D)
	ld (bc), a
;src//graphics/graphics.c:130: textProperties.font_pts = font_tiny_pts;
	ld hl, #_font_tiny_pts
	ld ((_textProperties + 0x0001)), hl
;src//graphics/graphics.c:131: textProperties.font_pos = font_tiny_pos;
	ld hl, #_font_tiny_pos
	ld ((_textProperties + 0x0003)), hl
;src//graphics/graphics.c:132: textProperties.font_len = font_tiny_len;
	ld hl, #_font_tiny_len
	ld ((_textProperties + 0x0005)), hl
;src//graphics/graphics.c:134: textProperties.value = 1;
	ld hl, #(_textProperties + 0x000c)
	ld (hl), #0x01
;src//graphics/graphics.c:135: textProperties.color = FWhite + BTransparent;
	ld hl, #(_textProperties + 0x000d)
	ld (hl), #0xf0
;src//graphics/graphics.c:136: textProperties.sz = 1;
	ld hl, #(_textProperties + 0x000e)
	ld (hl), #0x01
;src//graphics/graphics.c:137: textProperties.space_between_lines = 7;
	ld hl, #(_textProperties + 0x0007)
	ld (hl), #0x07
;src//graphics/graphics.c:139: setROI(0,0,255,191);
	ld de, #0xbfff
	push de
	xor a
	push af
	inc sp
	xor a
	push af
	inc sp
	call _setROI
;src//graphics/graphics.c:140: }
	ld sp, ix
	pop ix
	ret
_Coffset_y:
	.word #0x0000
	.word #0x0001
	.word #0x0002
	.word #0x0003
	.word #0x0004
	.word #0x0005
	.word #0x0006
	.word #0x0007
	.word #0x0008
	.word #0x0009
	.word #0x000a
	.word #0x000b
	.word #0x000c
	.word #0x000d
	.word #0x000e
	.word #0x000f
	.word #0x0010
	.word #0x0011
	.word #0x0012
	.word #0x0013
	.word #0x0014
	.word #0x0015
	.word #0x0016
	.word #0x0017
	.word #0x0018
	.word #0x0019
	.word #0x001a
	.word #0x001b
	.word #0x001c
	.word #0x001d
	.word #0x001e
	.word #0x001f
	.word #0x0020
	.word #0x0021
	.word #0x0022
	.word #0x0023
	.word #0x0024
	.word #0x0025
	.word #0x0026
	.word #0x0027
	.word #0x0028
	.word #0x0029
	.word #0x002a
	.word #0x002b
	.word #0x002c
	.word #0x002d
	.word #0x002e
	.word #0x002f
	.word #0x0030
	.word #0x0031
	.word #0x0032
	.word #0x0033
	.word #0x0034
	.word #0x0035
	.word #0x0036
	.word #0x0037
	.word #0x0038
	.word #0x0039
	.word #0x003a
	.word #0x003b
	.word #0x003c
	.word #0x003d
	.word #0x003e
	.word #0x003f
	.word #0x0800
	.word #0x0801
	.word #0x0802
	.word #0x0803
	.word #0x0804
	.word #0x0805
	.word #0x0806
	.word #0x0807
	.word #0x0808
	.word #0x0809
	.word #0x080a
	.word #0x080b
	.word #0x080c
	.word #0x080d
	.word #0x080e
	.word #0x080f
	.word #0x0810
	.word #0x0811
	.word #0x0812
	.word #0x0813
	.word #0x0814
	.word #0x0815
	.word #0x0816
	.word #0x0817
	.word #0x0818
	.word #0x0819
	.word #0x081a
	.word #0x081b
	.word #0x081c
	.word #0x081d
	.word #0x081e
	.word #0x081f
	.word #0x0820
	.word #0x0821
	.word #0x0822
	.word #0x0823
	.word #0x0824
	.word #0x0825
	.word #0x0826
	.word #0x0827
	.word #0x0828
	.word #0x0829
	.word #0x082a
	.word #0x082b
	.word #0x082c
	.word #0x082d
	.word #0x082e
	.word #0x082f
	.word #0x0830
	.word #0x0831
	.word #0x0832
	.word #0x0833
	.word #0x0834
	.word #0x0835
	.word #0x0836
	.word #0x0837
	.word #0x0838
	.word #0x0839
	.word #0x083a
	.word #0x083b
	.word #0x083c
	.word #0x083d
	.word #0x083e
	.word #0x083f
	.word #0x1000
	.word #0x1001
	.word #0x1002
	.word #0x1003
	.word #0x1004
	.word #0x1005
	.word #0x1006
	.word #0x1007
	.word #0x1008
	.word #0x1009
	.word #0x100a
	.word #0x100b
	.word #0x100c
	.word #0x100d
	.word #0x100e
	.word #0x100f
	.word #0x1010
	.word #0x1011
	.word #0x1012
	.word #0x1013
	.word #0x1014
	.word #0x1015
	.word #0x1016
	.word #0x1017
	.word #0x1018
	.word #0x1019
	.word #0x101a
	.word #0x101b
	.word #0x101c
	.word #0x101d
	.word #0x101e
	.word #0x101f
	.word #0x1020
	.word #0x1021
	.word #0x1022
	.word #0x1023
	.word #0x1024
	.word #0x1025
	.word #0x1026
	.word #0x1027
	.word #0x1028
	.word #0x1029
	.word #0x102a
	.word #0x102b
	.word #0x102c
	.word #0x102d
	.word #0x102e
	.word #0x102f
	.word #0x1030
	.word #0x1031
	.word #0x1032
	.word #0x1033
	.word #0x1034
	.word #0x1035
	.word #0x1036
	.word #0x1037
	.word #0x1038
	.word #0x1039
	.word #0x103a
	.word #0x103b
	.word #0x103c
	.word #0x103d
	.word #0x103e
	.word #0x103f
	.word #0x1800
	.word #0x1801
	.word #0x1802
	.word #0x1803
	.word #0x1804
	.word #0x1805
	.word #0x1806
	.word #0x1807
	.word #0x1808
	.word #0x1809
	.word #0x180a
	.word #0x180b
	.word #0x180c
	.word #0x180d
	.word #0x180e
	.word #0x180f
	.word #0x1810
	.word #0x1811
	.word #0x1812
	.word #0x1813
	.word #0x1814
	.word #0x1815
	.word #0x1816
	.word #0x1817
	.word #0x1818
	.word #0x1819
	.word #0x181a
	.word #0x181b
	.word #0x181c
	.word #0x181d
	.word #0x181e
	.word #0x181f
	.word #0x1820
	.word #0x1821
	.word #0x1822
	.word #0x1823
	.word #0x1824
	.word #0x1825
	.word #0x1826
	.word #0x1827
	.word #0x1828
	.word #0x1829
	.word #0x182a
	.word #0x182b
	.word #0x182c
	.word #0x182d
	.word #0x182e
	.word #0x182f
	.word #0x1830
	.word #0x1831
	.word #0x1832
	.word #0x1833
	.word #0x1834
	.word #0x1835
	.word #0x1836
	.word #0x1837
	.word #0x1838
	.word #0x1839
	.word #0x183a
	.word #0x183b
	.word #0x183c
	.word #0x183d
	.word #0x183e
	.word #0x183f
_Coffset_x:
	.word #0x0000
	.word #0x0000
	.word #0x0000
	.word #0x0000
	.word #0x0000
	.word #0x0000
	.word #0x0000
	.word #0x0000
	.word #0x0040
	.word #0x0040
	.word #0x0040
	.word #0x0040
	.word #0x0040
	.word #0x0040
	.word #0x0040
	.word #0x0040
	.word #0x0080
	.word #0x0080
	.word #0x0080
	.word #0x0080
	.word #0x0080
	.word #0x0080
	.word #0x0080
	.word #0x0080
	.word #0x00c0
	.word #0x00c0
	.word #0x00c0
	.word #0x00c0
	.word #0x00c0
	.word #0x00c0
	.word #0x00c0
	.word #0x00c0
	.word #0x0100
	.word #0x0100
	.word #0x0100
	.word #0x0100
	.word #0x0100
	.word #0x0100
	.word #0x0100
	.word #0x0100
	.word #0x0140
	.word #0x0140
	.word #0x0140
	.word #0x0140
	.word #0x0140
	.word #0x0140
	.word #0x0140
	.word #0x0140
	.word #0x0180
	.word #0x0180
	.word #0x0180
	.word #0x0180
	.word #0x0180
	.word #0x0180
	.word #0x0180
	.word #0x0180
	.word #0x01c0
	.word #0x01c0
	.word #0x01c0
	.word #0x01c0
	.word #0x01c0
	.word #0x01c0
	.word #0x01c0
	.word #0x01c0
	.word #0x0200
	.word #0x0200
	.word #0x0200
	.word #0x0200
	.word #0x0200
	.word #0x0200
	.word #0x0200
	.word #0x0200
	.word #0x0240
	.word #0x0240
	.word #0x0240
	.word #0x0240
	.word #0x0240
	.word #0x0240
	.word #0x0240
	.word #0x0240
	.word #0x0280
	.word #0x0280
	.word #0x0280
	.word #0x0280
	.word #0x0280
	.word #0x0280
	.word #0x0280
	.word #0x0280
	.word #0x02c0
	.word #0x02c0
	.word #0x02c0
	.word #0x02c0
	.word #0x02c0
	.word #0x02c0
	.word #0x02c0
	.word #0x02c0
	.word #0x0300
	.word #0x0300
	.word #0x0300
	.word #0x0300
	.word #0x0300
	.word #0x0300
	.word #0x0300
	.word #0x0300
	.word #0x0340
	.word #0x0340
	.word #0x0340
	.word #0x0340
	.word #0x0340
	.word #0x0340
	.word #0x0340
	.word #0x0340
	.word #0x0380
	.word #0x0380
	.word #0x0380
	.word #0x0380
	.word #0x0380
	.word #0x0380
	.word #0x0380
	.word #0x0380
	.word #0x03c0
	.word #0x03c0
	.word #0x03c0
	.word #0x03c0
	.word #0x03c0
	.word #0x03c0
	.word #0x03c0
	.word #0x03c0
	.word #0x0400
	.word #0x0400
	.word #0x0400
	.word #0x0400
	.word #0x0400
	.word #0x0400
	.word #0x0400
	.word #0x0400
	.word #0x0440
	.word #0x0440
	.word #0x0440
	.word #0x0440
	.word #0x0440
	.word #0x0440
	.word #0x0440
	.word #0x0440
	.word #0x0480
	.word #0x0480
	.word #0x0480
	.word #0x0480
	.word #0x0480
	.word #0x0480
	.word #0x0480
	.word #0x0480
	.word #0x04c0
	.word #0x04c0
	.word #0x04c0
	.word #0x04c0
	.word #0x04c0
	.word #0x04c0
	.word #0x04c0
	.word #0x04c0
	.word #0x0500
	.word #0x0500
	.word #0x0500
	.word #0x0500
	.word #0x0500
	.word #0x0500
	.word #0x0500
	.word #0x0500
	.word #0x0540
	.word #0x0540
	.word #0x0540
	.word #0x0540
	.word #0x0540
	.word #0x0540
	.word #0x0540
	.word #0x0540
	.word #0x0580
	.word #0x0580
	.word #0x0580
	.word #0x0580
	.word #0x0580
	.word #0x0580
	.word #0x0580
	.word #0x0580
	.word #0x05c0
	.word #0x05c0
	.word #0x05c0
	.word #0x05c0
	.word #0x05c0
	.word #0x05c0
	.word #0x05c0
	.word #0x05c0
	.word #0x0600
	.word #0x0600
	.word #0x0600
	.word #0x0600
	.word #0x0600
	.word #0x0600
	.word #0x0600
	.word #0x0600
	.word #0x0640
	.word #0x0640
	.word #0x0640
	.word #0x0640
	.word #0x0640
	.word #0x0640
	.word #0x0640
	.word #0x0640
	.word #0x0680
	.word #0x0680
	.word #0x0680
	.word #0x0680
	.word #0x0680
	.word #0x0680
	.word #0x0680
	.word #0x0680
	.word #0x06c0
	.word #0x06c0
	.word #0x06c0
	.word #0x06c0
	.word #0x06c0
	.word #0x06c0
	.word #0x06c0
	.word #0x06c0
	.word #0x0700
	.word #0x0700
	.word #0x0700
	.word #0x0700
	.word #0x0700
	.word #0x0700
	.word #0x0700
	.word #0x0700
	.word #0x0740
	.word #0x0740
	.word #0x0740
	.word #0x0740
	.word #0x0740
	.word #0x0740
	.word #0x0740
	.word #0x0740
	.word #0x0780
	.word #0x0780
	.word #0x0780
	.word #0x0780
	.word #0x0780
	.word #0x0780
	.word #0x0780
	.word #0x0780
	.word #0x07c0
	.word #0x07c0
	.word #0x07c0
	.word #0x07c0
	.word #0x07c0
	.word #0x07c0
	.word #0x07c0
	.word #0x07c0
_Cshift8:
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
	.byte #0x80  ; 128
	.byte #0x40  ; 64
	.byte #0x20  ; 32
	.byte #0x10  ; 16
	.byte #0x08  ; 8
	.byte #0x04  ; 4
	.byte #0x02  ; 2
	.byte #0x01  ; 1
;src//graphics/graphics.c:143: void undoPoint() {
;	---------------------------------
; Function undoPoint
; ---------------------------------
_undoPoint::
	push ix
	ld ix, #0
	add ix, sp
	push af
;src//graphics/graphics.c:145: uint16_t pos = history_location[history_pt];
	ld bc, #(_history_location + 0)
	ld iy, #_history_pt
	ld l, 0 (iy)
	ld h, #0x00
	add hl, hl
	ex (sp), hl
	pop hl
	push hl
	add hl, bc
	ld c, (hl)
	inc hl
	ld b, (hl)
;src//graphics/graphics.c:146: if (pos<(16*1024)) {
	ld a, b
	sub #0x40
	jr nc, 00102$
;sdcc_msx/inc/tms99X8.h:217: VDP1 = dst & 0xFF; 
	ld a, c
	out (#_VDP1), a
;sdcc_msx/inc/tms99X8.h:218: VDP1 = 0x40 | (dst>>8);
	ld e, b
	ld d, #0x00
	ld a, e
	or #0x40
	out (#_VDP1), a
;src//graphics/graphics.c:148: TMS99X8_write(MODE2_ADDRESS_PG + (screen_copy[pos] = history_value[history_pt]));
	ld hl, #_screen_copy
	add hl, bc
	ex de, hl
	ld a, #<(_history_value)
	ld hl, #_history_pt
	add a, (hl)
	ld c, a
	ld a, #>(_history_value)
	adc a, #0x00
	ld b, a
	ld a, (bc)
	ld (de), a
	out (#_VDP0), a
00102$:
;src//graphics/graphics.c:150: history_pt = (history_pt+255);
	ld hl, #(_history_pt + 0)
	dec (hl)
;src//graphics/graphics.c:151: }
	ld sp, ix
	pop ix
	ret
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
	.ascii "some random sentence"
some_label:
some_constant .equ 1
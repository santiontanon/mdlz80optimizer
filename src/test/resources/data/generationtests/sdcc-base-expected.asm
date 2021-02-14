;--------------------------------------------------------
; File Created by SDCC : free open source ANSI-C Compiler
; Version 3.9.0 #11195 (Mac OS X x86_64)
;--------------------------------------------------------
;--------------------------------------------------------
; Public variables in this module
;--------------------------------------------------------
;--------------------------------------------------------
; special function registers
;--------------------------------------------------------
_VDP0: equ 0x0098
_VDP1: equ 0x0099
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
s__DATA: equ $
_TMS99X8: equ 0xf3df
_TMS99X8_status: equ 0xf3e7
_SA:
	ds 128, 0
_history_location:
	ds 512, 0
_history_value:
	ds 256, 0
_history_shift:
	ds 256, 0
_history_pt:
	ds 1, 0
_ROI_x0:
	ds 1, 0
_ROI_x1:
	ds 1, 0
_ROI_y0:
	ds 1, 0
_ROI_y1:
	ds 1, 0
_textProperties:
	ds 20, 0
_screen_copy: equ 0xd000
_offset_x: equ 0xe800
_offset_y: equ 0xea00
_shift8: equ 0xec00
;--------------------------------------------------------
; ram data
;--------------------------------------------------------
s__INITIALIZED:
;--------------------------------------------------------
; absolute external ram data
;--------------------------------------------------------
s__DABS:
;--------------------------------------------------------
; global & static initialisations
;--------------------------------------------------------
s__HOME:
s__GSINIT:
s__GSFINAL:
;--------------------------------------------------------
; Home
;--------------------------------------------------------
;--------------------------------------------------------
; code
;--------------------------------------------------------
s__CODE:
;src//graphics/graphics.c:89: void initCanvas() {
;	---------------------------------
; Function initCanvas
; ---------------------------------
_initCanvas:
	push ix
	ld ix, 0
	add ix, sp
	ld hl, -7
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:94: TMS99X8_activateMode2(MODE2_ALL_ROWS); 
	ld a, 0x07
	push af
	inc sp
	call _TMS99X8_activateMode2
	inc sp
;src//graphics/graphics.c:97: for (uint8_t n=0; n<32; n++) { SA[n].y = 209; }
	ld c, 0x00
_initCanvas00216:
	ld a, c
	sub 0x20
	jr nc, _initCanvas00101
	ld l, c
	ld h, 0x00
	add hl, hl
	add hl, hl
	ld de, _SA
	add hl, de
	ld (hl), 0xd1
	inc c
	jr _initCanvas00216
_initCanvas00101:
;src//graphics/graphics.c:98: TMS99X8_writeSpriteAttributes(0,SA);
;sdcc_msx/inc/tms99X8.h:294: TMS99X8_memcpy(MODE2_ADDRESS_SA0, (const uint8_t *)sa, sizeof(T_SA)); 
;sdcc_msx/inc/tms99X8.h:217: VDP1 = dst & 0xFF; 
	ld a, 0x00
	out (_VDP1), a
;sdcc_msx/inc/tms99X8.h:218: VDP1 = 0x40 | (dst>>8);
	ld a, 0x1f
	or 0x40
	out (_VDP1), a
;sdcc_msx/inc/tms99X8.h:273: else if (size == 128) { TMS99X8_setPtr(dst); TMS99X8_memcpy128(src); }
	ld hl, _SA
	call _TMS99X8_memcpy128
;src//graphics/graphics.c:101: TMS99X8.sprites16 = true;
	ld hl, _TMS99X8 + 0x0001
	set 1, (hl)
;sdcc_msx/inc/tms99X8.h:188: register uint8_t *r = (uint8_t *)TMS99X8;
	ld bc, _TMS99X8
;sdcc_msx/inc/tms99X8.h:189: VDP1 = *r++;
	ld a, (bc)
	out (_VDP1), a
	inc bc
;sdcc_msx/inc/tms99X8.h:190: VDP1 = 0x80 | 0;
	ld a, 0x80
	out (_VDP1), a
;sdcc_msx/inc/tms99X8.h:191: VDP1 = *r++;
	ld a, (bc)
	out (_VDP1), a
;sdcc_msx/inc/tms99X8.h:192: VDP1 = 0x80 | 1;
	ld a, 0x81
	out (_VDP1), a
;src//graphics/graphics.c:107: do {
	ld (ix + -1), 0x00
_initCanvas00102:
;src//graphics/graphics.c:108: scratchpad[((i<<5)&0xFF) + (i>>3)] = i;
	ld l, (ix + -1)
	ld h, 0x00
	add hl, hl
	add hl, hl
	add hl, hl
	add hl, hl
	add hl, hl
	ld (ix + -7), l
	ld (ix + -6), 0x00
	ld a, (ix + -1)
	rrca
	rrca
	rrca
	and 0x1f
	ld (ix + -5), a
	ld (ix + -4), 0x00
	ld a, (ix + -7)
	add a, (ix + -5)
	ld (ix + -3), a
	ld a, (ix + -6)
	adc a, (ix + -4)
	ld (ix + -2), a
	ld a, _scratchpad & 0x00ff
	add a, (ix + -3)
	ld (ix + -5), a
	ld a, (_scratchpad & 0xff00) >> 8
	adc a, (ix + -2)
	ld (ix + -4), a
	pop bc
	pop hl
	push hl
	push bc
	ld a, (ix + -1)
	ld (hl), a
;src//graphics/graphics.c:109: } while (++i != 0);
	inc (ix + -1)
	ld a, (ix + -1)
	or a
	jr nz, _initCanvas00102
;src//graphics/graphics.c:112: TMS99X8_memcpy(MODE2_ADDRESS_PN0 + 0x0000, (const uint8_t *)scratchpad, 256);    
;sdcc_msx/inc/tms99X8.h:274: else TMS99X8_memcpy_slow(dst,src,size);
	ld hl, 0x0100
	push hl
	ld hl, _scratchpad
	push hl
	ld hl, 0x1800
	push hl
	call _TMS99X8_memcpy_slow
	ld hl, 6
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:113: TMS99X8_memcpy(MODE2_ADDRESS_PN0 + 0x0100, (const uint8_t *)scratchpad, 256);    
;sdcc_msx/inc/tms99X8.h:274: else TMS99X8_memcpy_slow(dst,src,size);
	ld hl, 0x0100
	push hl
	ld hl, _scratchpad
	push hl
	ld hl, 0x1900
	push hl
	call _TMS99X8_memcpy_slow
	ld hl, 6
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:114: TMS99X8_memcpy(MODE2_ADDRESS_PN0 + 0x0200, (const uint8_t *)scratchpad, 256);    
;sdcc_msx/inc/tms99X8.h:274: else TMS99X8_memcpy_slow(dst,src,size);
	ld hl, 0x0100
	push hl
	ld hl, _scratchpad
	push hl
	ld hl, 0x1a00
	push hl
	call _TMS99X8_memcpy_slow
	ld hl, 6
	add hl, sp
	ld sp, hl
;src//graphics/graphics.c:116: TMS99X8_memset(MODE2_ADDRESS_PG, 0, sizeof(T_PG));
	ld hl, 0x1800
	push hl
	xor a
	push af
	inc sp
	ld h, 0x00
	push hl
	call _TMS99X8_memset
	pop af
	pop af
	inc sp
;src//graphics/graphics.c:117: memset(screen_copy,0,sizeof(screen_copy));
	ld hl, _screen_copy
	ld (hl), 0x00
	ld e, l
	ld d, h
	inc de
	ld bc, 0x17ff
	ldir
;src//graphics/graphics.c:119: memcpy(offset_x, Coffset_x, sizeof(offset_x));
	ld de, _offset_x
	ld hl, _Coffset_x
	ld bc, 0x0200
	ldir
;src//graphics/graphics.c:120: memcpy(offset_y, Coffset_y, sizeof(offset_y));
	ld de, _offset_y
	ld hl, _Coffset_y
	ld bc, 0x0200
	ldir
;src//graphics/graphics.c:121: memcpy(shift8, Cshift8, sizeof(shift8));
	ld de, _shift8
	ld hl, _Cshift8
	ld bc, 0x0100
	ldir
;src//graphics/graphics.c:123: TMS99X8_memset(MODE2_ADDRESS_CT, FWhite + BTransparent, sizeof(T_CT));
	ld hl, 0x1800
	push hl
	ld a, 0xf0
	push af
	inc sp
	ld h, 0x20
	push hl
	call _TMS99X8_memset
	pop af
	pop af
	inc sp
;src//graphics/graphics.c:125: history_pt = 0;
	ld hl, _history_pt + 0
	ld (hl), 0x00
;src//graphics/graphics.c:127: memset(&textProperties,0,sizeof(textProperties));
	ld bc, _textProperties + 0
	ld l, c
	ld h, b
	push bc
	ld b, 0x0a
_initCanvas00268:
	xor a
	ld (hl), a
	inc hl
	ld (hl), a
	inc hl
	djnz _initCanvas00268
	pop bc
;src//graphics/graphics.c:129: textProperties.font_segment = MODULE_SEGMENT(font_tiny,PAGE_D);
	ld a, _MAPPER_MODULE_font_tiny_PAGE_D & 0x00ff
	ld (bc), a
;src//graphics/graphics.c:130: textProperties.font_pts = font_tiny_pts;
	ld hl, _font_tiny_pts
	ld ((_textProperties + 0x0001)), hl
;src//graphics/graphics.c:131: textProperties.font_pos = font_tiny_pos;
	ld hl, _font_tiny_pos
	ld ((_textProperties + 0x0003)), hl
;src//graphics/graphics.c:132: textProperties.font_len = font_tiny_len;
	ld hl, _font_tiny_len
	ld ((_textProperties + 0x0005)), hl
;src//graphics/graphics.c:134: textProperties.value = 1;
	ld hl, _textProperties + 0x000c
	ld (hl), 0x01
;src//graphics/graphics.c:135: textProperties.color = FWhite + BTransparent;
	ld hl, _textProperties + 0x000d
	ld (hl), 0xf0
;src//graphics/graphics.c:136: textProperties.sz = 1;
	ld hl, _textProperties + 0x000e
	ld (hl), 0x01
;src//graphics/graphics.c:137: textProperties.space_between_lines = 7;
	ld hl, _textProperties + 0x0007
	ld (hl), 0x07
;src//graphics/graphics.c:139: setROI(0,0,255,191);
	ld de, 0xbfff
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
	dw 0x0000
	dw 0x0001
	dw 0x0002
	dw 0x0003
	dw 0x0004
	dw 0x0005
	dw 0x0006
	dw 0x0007
	dw 0x0008
	dw 0x0009
	dw 0x000a
	dw 0x000b
	dw 0x000c
	dw 0x000d
	dw 0x000e
	dw 0x000f
	dw 0x0010
	dw 0x0011
	dw 0x0012
	dw 0x0013
	dw 0x0014
	dw 0x0015
	dw 0x0016
	dw 0x0017
	dw 0x0018
	dw 0x0019
	dw 0x001a
	dw 0x001b
	dw 0x001c
	dw 0x001d
	dw 0x001e
	dw 0x001f
	dw 0x0020
	dw 0x0021
	dw 0x0022
	dw 0x0023
	dw 0x0024
	dw 0x0025
	dw 0x0026
	dw 0x0027
	dw 0x0028
	dw 0x0029
	dw 0x002a
	dw 0x002b
	dw 0x002c
	dw 0x002d
	dw 0x002e
	dw 0x002f
	dw 0x0030
	dw 0x0031
	dw 0x0032
	dw 0x0033
	dw 0x0034
	dw 0x0035
	dw 0x0036
	dw 0x0037
	dw 0x0038
	dw 0x0039
	dw 0x003a
	dw 0x003b
	dw 0x003c
	dw 0x003d
	dw 0x003e
	dw 0x003f
	dw 0x0800
	dw 0x0801
	dw 0x0802
	dw 0x0803
	dw 0x0804
	dw 0x0805
	dw 0x0806
	dw 0x0807
	dw 0x0808
	dw 0x0809
	dw 0x080a
	dw 0x080b
	dw 0x080c
	dw 0x080d
	dw 0x080e
	dw 0x080f
	dw 0x0810
	dw 0x0811
	dw 0x0812
	dw 0x0813
	dw 0x0814
	dw 0x0815
	dw 0x0816
	dw 0x0817
	dw 0x0818
	dw 0x0819
	dw 0x081a
	dw 0x081b
	dw 0x081c
	dw 0x081d
	dw 0x081e
	dw 0x081f
	dw 0x0820
	dw 0x0821
	dw 0x0822
	dw 0x0823
	dw 0x0824
	dw 0x0825
	dw 0x0826
	dw 0x0827
	dw 0x0828
	dw 0x0829
	dw 0x082a
	dw 0x082b
	dw 0x082c
	dw 0x082d
	dw 0x082e
	dw 0x082f
	dw 0x0830
	dw 0x0831
	dw 0x0832
	dw 0x0833
	dw 0x0834
	dw 0x0835
	dw 0x0836
	dw 0x0837
	dw 0x0838
	dw 0x0839
	dw 0x083a
	dw 0x083b
	dw 0x083c
	dw 0x083d
	dw 0x083e
	dw 0x083f
	dw 0x1000
	dw 0x1001
	dw 0x1002
	dw 0x1003
	dw 0x1004
	dw 0x1005
	dw 0x1006
	dw 0x1007
	dw 0x1008
	dw 0x1009
	dw 0x100a
	dw 0x100b
	dw 0x100c
	dw 0x100d
	dw 0x100e
	dw 0x100f
	dw 0x1010
	dw 0x1011
	dw 0x1012
	dw 0x1013
	dw 0x1014
	dw 0x1015
	dw 0x1016
	dw 0x1017
	dw 0x1018
	dw 0x1019
	dw 0x101a
	dw 0x101b
	dw 0x101c
	dw 0x101d
	dw 0x101e
	dw 0x101f
	dw 0x1020
	dw 0x1021
	dw 0x1022
	dw 0x1023
	dw 0x1024
	dw 0x1025
	dw 0x1026
	dw 0x1027
	dw 0x1028
	dw 0x1029
	dw 0x102a
	dw 0x102b
	dw 0x102c
	dw 0x102d
	dw 0x102e
	dw 0x102f
	dw 0x1030
	dw 0x1031
	dw 0x1032
	dw 0x1033
	dw 0x1034
	dw 0x1035
	dw 0x1036
	dw 0x1037
	dw 0x1038
	dw 0x1039
	dw 0x103a
	dw 0x103b
	dw 0x103c
	dw 0x103d
	dw 0x103e
	dw 0x103f
	dw 0x1800
	dw 0x1801
	dw 0x1802
	dw 0x1803
	dw 0x1804
	dw 0x1805
	dw 0x1806
	dw 0x1807
	dw 0x1808
	dw 0x1809
	dw 0x180a
	dw 0x180b
	dw 0x180c
	dw 0x180d
	dw 0x180e
	dw 0x180f
	dw 0x1810
	dw 0x1811
	dw 0x1812
	dw 0x1813
	dw 0x1814
	dw 0x1815
	dw 0x1816
	dw 0x1817
	dw 0x1818
	dw 0x1819
	dw 0x181a
	dw 0x181b
	dw 0x181c
	dw 0x181d
	dw 0x181e
	dw 0x181f
	dw 0x1820
	dw 0x1821
	dw 0x1822
	dw 0x1823
	dw 0x1824
	dw 0x1825
	dw 0x1826
	dw 0x1827
	dw 0x1828
	dw 0x1829
	dw 0x182a
	dw 0x182b
	dw 0x182c
	dw 0x182d
	dw 0x182e
	dw 0x182f
	dw 0x1830
	dw 0x1831
	dw 0x1832
	dw 0x1833
	dw 0x1834
	dw 0x1835
	dw 0x1836
	dw 0x1837
	dw 0x1838
	dw 0x1839
	dw 0x183a
	dw 0x183b
	dw 0x183c
	dw 0x183d
	dw 0x183e
	dw 0x183f
_Coffset_x:
	dw 0x0000
	dw 0x0000
	dw 0x0000
	dw 0x0000
	dw 0x0000
	dw 0x0000
	dw 0x0000
	dw 0x0000
	dw 0x0040
	dw 0x0040
	dw 0x0040
	dw 0x0040
	dw 0x0040
	dw 0x0040
	dw 0x0040
	dw 0x0040
	dw 0x0080
	dw 0x0080
	dw 0x0080
	dw 0x0080
	dw 0x0080
	dw 0x0080
	dw 0x0080
	dw 0x0080
	dw 0x00c0
	dw 0x00c0
	dw 0x00c0
	dw 0x00c0
	dw 0x00c0
	dw 0x00c0
	dw 0x00c0
	dw 0x00c0
	dw 0x0100
	dw 0x0100
	dw 0x0100
	dw 0x0100
	dw 0x0100
	dw 0x0100
	dw 0x0100
	dw 0x0100
	dw 0x0140
	dw 0x0140
	dw 0x0140
	dw 0x0140
	dw 0x0140
	dw 0x0140
	dw 0x0140
	dw 0x0140
	dw 0x0180
	dw 0x0180
	dw 0x0180
	dw 0x0180
	dw 0x0180
	dw 0x0180
	dw 0x0180
	dw 0x0180
	dw 0x01c0
	dw 0x01c0
	dw 0x01c0
	dw 0x01c0
	dw 0x01c0
	dw 0x01c0
	dw 0x01c0
	dw 0x01c0
	dw 0x0200
	dw 0x0200
	dw 0x0200
	dw 0x0200
	dw 0x0200
	dw 0x0200
	dw 0x0200
	dw 0x0200
	dw 0x0240
	dw 0x0240
	dw 0x0240
	dw 0x0240
	dw 0x0240
	dw 0x0240
	dw 0x0240
	dw 0x0240
	dw 0x0280
	dw 0x0280
	dw 0x0280
	dw 0x0280
	dw 0x0280
	dw 0x0280
	dw 0x0280
	dw 0x0280
	dw 0x02c0
	dw 0x02c0
	dw 0x02c0
	dw 0x02c0
	dw 0x02c0
	dw 0x02c0
	dw 0x02c0
	dw 0x02c0
	dw 0x0300
	dw 0x0300
	dw 0x0300
	dw 0x0300
	dw 0x0300
	dw 0x0300
	dw 0x0300
	dw 0x0300
	dw 0x0340
	dw 0x0340
	dw 0x0340
	dw 0x0340
	dw 0x0340
	dw 0x0340
	dw 0x0340
	dw 0x0340
	dw 0x0380
	dw 0x0380
	dw 0x0380
	dw 0x0380
	dw 0x0380
	dw 0x0380
	dw 0x0380
	dw 0x0380
	dw 0x03c0
	dw 0x03c0
	dw 0x03c0
	dw 0x03c0
	dw 0x03c0
	dw 0x03c0
	dw 0x03c0
	dw 0x03c0
	dw 0x0400
	dw 0x0400
	dw 0x0400
	dw 0x0400
	dw 0x0400
	dw 0x0400
	dw 0x0400
	dw 0x0400
	dw 0x0440
	dw 0x0440
	dw 0x0440
	dw 0x0440
	dw 0x0440
	dw 0x0440
	dw 0x0440
	dw 0x0440
	dw 0x0480
	dw 0x0480
	dw 0x0480
	dw 0x0480
	dw 0x0480
	dw 0x0480
	dw 0x0480
	dw 0x0480
	dw 0x04c0
	dw 0x04c0
	dw 0x04c0
	dw 0x04c0
	dw 0x04c0
	dw 0x04c0
	dw 0x04c0
	dw 0x04c0
	dw 0x0500
	dw 0x0500
	dw 0x0500
	dw 0x0500
	dw 0x0500
	dw 0x0500
	dw 0x0500
	dw 0x0500
	dw 0x0540
	dw 0x0540
	dw 0x0540
	dw 0x0540
	dw 0x0540
	dw 0x0540
	dw 0x0540
	dw 0x0540
	dw 0x0580
	dw 0x0580
	dw 0x0580
	dw 0x0580
	dw 0x0580
	dw 0x0580
	dw 0x0580
	dw 0x0580
	dw 0x05c0
	dw 0x05c0
	dw 0x05c0
	dw 0x05c0
	dw 0x05c0
	dw 0x05c0
	dw 0x05c0
	dw 0x05c0
	dw 0x0600
	dw 0x0600
	dw 0x0600
	dw 0x0600
	dw 0x0600
	dw 0x0600
	dw 0x0600
	dw 0x0600
	dw 0x0640
	dw 0x0640
	dw 0x0640
	dw 0x0640
	dw 0x0640
	dw 0x0640
	dw 0x0640
	dw 0x0640
	dw 0x0680
	dw 0x0680
	dw 0x0680
	dw 0x0680
	dw 0x0680
	dw 0x0680
	dw 0x0680
	dw 0x0680
	dw 0x06c0
	dw 0x06c0
	dw 0x06c0
	dw 0x06c0
	dw 0x06c0
	dw 0x06c0
	dw 0x06c0
	dw 0x06c0
	dw 0x0700
	dw 0x0700
	dw 0x0700
	dw 0x0700
	dw 0x0700
	dw 0x0700
	dw 0x0700
	dw 0x0700
	dw 0x0740
	dw 0x0740
	dw 0x0740
	dw 0x0740
	dw 0x0740
	dw 0x0740
	dw 0x0740
	dw 0x0740
	dw 0x0780
	dw 0x0780
	dw 0x0780
	dw 0x0780
	dw 0x0780
	dw 0x0780
	dw 0x0780
	dw 0x0780
	dw 0x07c0
	dw 0x07c0
	dw 0x07c0
	dw 0x07c0
	dw 0x07c0
	dw 0x07c0
	dw 0x07c0
	dw 0x07c0
_Cshift8:
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
	db 0x80  ; 128
	db 0x40  ; 64
	db 0x20  ; 32
	db 0x10  ; 16
	db 0x08  ; 8
	db 0x04  ; 4
	db 0x02  ; 2
	db 0x01  ; 1
;src//graphics/graphics.c:143: void undoPoint() {
;	---------------------------------
; Function undoPoint
; ---------------------------------
_undoPoint:
	push ix
	ld ix, 0
	add ix, sp
	push af
;src//graphics/graphics.c:145: uint16_t pos = history_location[history_pt];
	ld bc, _history_location + 0
	ld iy, _history_pt
	ld l, (iy + 0)
	ld h, 0x00
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
	sub 0x40
	jr nc, _undoPoint00102
;sdcc_msx/inc/tms99X8.h:217: VDP1 = dst & 0xFF; 
	ld a, c
	out (_VDP1), a
;sdcc_msx/inc/tms99X8.h:218: VDP1 = 0x40 | (dst>>8);
	ld e, b
	ld d, 0x00
	ld a, e
	or 0x40
	out (_VDP1), a
;src//graphics/graphics.c:148: TMS99X8_write(MODE2_ADDRESS_PG + (screen_copy[pos] = history_value[history_pt]));
	ld hl, _screen_copy
	add hl, bc
	ex de, hl
	ld a, _history_value & 0x00ff
	ld hl, _history_pt
	add a, (hl)
	ld c, a
	ld a, (_history_value & 0xff00) >> 8
	adc a, 0x00
	ld b, a
	ld a, (bc)
	ld (de), a
	out (_VDP0), a
_undoPoint00102:
;src//graphics/graphics.c:150: history_pt = (history_pt+255);
	ld hl, _history_pt + 0
	dec (hl)
;src//graphics/graphics.c:151: }
	ld sp, ix
	pop ix
	ret
s__INITIALIZER:
s__CABS:
	db "some random sentence"
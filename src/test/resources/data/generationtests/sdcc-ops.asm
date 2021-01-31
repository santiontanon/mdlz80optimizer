; Test case: 
.macro mymacro arg1,arg2
	ld c,arg1
	ld b,arg2
.endm

	ld de, #array1
	ld hl, #array2
	mymacro #0x04,#CONOUT
	ldir
	ld a,b
	ld	hl, #-9
	ld	bc, (#array1 + 1)
	ld	hl, #(array2 + 0x0003)
	ld ((array2 + 0x0008)), de
        ld e, #0b10011000
00105$:
	jr 00105$

array1:
	.byte 0,0,0,0
array2:
	.byte 0,0,0,0
	CONOUT .equ 0x00

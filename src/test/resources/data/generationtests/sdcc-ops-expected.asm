; Test case: 
label:
	ld de, array1
	ld hl, array2
	ld c, 0x04
	ld b, CONOUT
	ldir
	ld a, b
	ld hl, -9
	ld bc, (array1 + 1)
	ld hl, array2 + 0x0003
	ld ((array2 + 0x0008)), de
    ld e, 0b10011000
label00105:
	jr label00105
array1:
	db 0, 0, 0, 0
array2:
	db 0, 0, 0, 0
CONOUT: equ 0x00

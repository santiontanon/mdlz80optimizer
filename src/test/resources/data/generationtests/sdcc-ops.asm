; Test case: 
	ld de, #array1
	ld hl, #array2
	ld bc, #0x0004
	ldir
	ld a,b
	ld	hl, #-9
	ld	bc, (#array1 + 1)
	ld	hl, #(array2 + 0x0003)
	ld ((array2 + 0x0008)), de
00105$:
	jr 00105$

array1:
	.byte 0,0,0,0
array2:
	.byte 0,0,0,0

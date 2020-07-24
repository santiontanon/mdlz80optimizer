; Test case:

	ld a,(val)
	or a
	jr nz,label1

	xor a		; this should be optimized
	ld (val),a
label1:

	ld b,3
label2:
	ld a,b
	ld (val),a
	djnz label2

	ld b,0		; this should be optimized
	ld a,b
	ld (val),a

loop:
	jr loop

val:
	db 0

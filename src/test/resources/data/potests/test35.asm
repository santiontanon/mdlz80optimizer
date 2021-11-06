; Test case:

	ld bc,4
loop1:
	inc hl
	dec bc
	ld a,b
	or c
	jr nz,loop1	; this whole block should be optimized

loop2:
	jr loop2
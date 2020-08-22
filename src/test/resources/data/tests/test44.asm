; test to check for repeated removal of unnecessary code:

	ld hl, var	; should be optimized
	inc hl	; should be optimized
	ld a,(hl)	; should be optimized
loop:
	jr loop

var:
	db 0, 1
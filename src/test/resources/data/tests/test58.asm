; Test: some test for removing useless code generated some times by SDCC

	xor a
	ld (hl),a
	xor a	; <-- this should be removed
	ld (de),a
	inc hl
	ld (hl),0  ; <-- this should be optimized to ld (hl),a
	inc hl
	ld a,(ix)
	ld (hl),a

	inc hl
	xor a  ; <-- these two instructions should be optimized to ld a,10
	add a,10
	ld (hl),a

	inc hl
	ld a,20  ; <-- these two should be optimized to ld a,20 | 100
	or 100

	inc hl
	ld b,a  ; <-- these two can be removed
	ld a,b
	ld (hl),a

loop:
	jr loop

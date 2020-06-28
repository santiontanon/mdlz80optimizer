; Test case: 
; - lines 4-5 should be optimized
; - lines 9-10 should be optimized to djnz

	ld hl,var1
	ld de,var2
	ld a,(hl)	; should be replaced by ldi
	ld (de),a
	inc hl
	inc de

	ld de,10	
	or a
	sbc hl,de	; should be replaced by add -10

	ld a,(var1)
	neg
	add a,10

loop:
	jp loop

var1:
	db 0
var2:
	db 0
; Test case: 
; - lines 3-6 should NOT be optimized by using hl, as hl is used by the push

	pop hl
	ld a,(var1)
	inc a
	ld (var1),a
	push hl
loop4:
	jp loop4

var1:
	db 0

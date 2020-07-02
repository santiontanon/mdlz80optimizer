; Test case: 
; - lines 3-6 should be optimized

	ld a,(var1)
	inc a
	ld (var1),a
loop4:
	jp loop4

var1:
	db 0
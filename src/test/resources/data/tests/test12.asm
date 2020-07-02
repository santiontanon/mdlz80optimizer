; Test case: 
; - lines 3-6 should be optimized (but keeping a)

	ld a,(var1)
	inc a
	ld (var1),a
	ld (var2),a
loop4:
	jp loop4

var1:
	db 0
var2:
	db 0
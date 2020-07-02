; Test case: 
; - lines 4-6 should not be optimized

	pop bc
	ld d,b
	ld e,c
	ld (var1),de
	ld (var2),bc

var1:
	dw 0
var2:
	dw 0
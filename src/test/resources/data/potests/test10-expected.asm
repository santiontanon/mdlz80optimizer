; Test case: 
; - lines 4-6 should be optimized

	pop bc
	ld (var1), bc

loop:
	jr loop

var1:
	dw 0
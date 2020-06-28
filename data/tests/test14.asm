; Test case: 
; - lines 4-5 should be optimized

	ld b,1
	ld c,2
	ld (var1),bc
loop4:
	jp loop4

var1:
	dw 0

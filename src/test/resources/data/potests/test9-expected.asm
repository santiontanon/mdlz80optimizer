; Test case: 

	pop bc
	ld (var1), bc
	ld (var2), bc
loop:
    jr loop

var1:
	dw 0
var2:
	dw 0
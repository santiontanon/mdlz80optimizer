; Test case: 
ONE: equ 1
	ld a, (value)
	or a
	call z, function1
	ld a, 2
	ld (value), a
__mdlrenamed__end:
	jr __mdlrenamed__end
function1:
	xor a
	ret
value:
	db 1
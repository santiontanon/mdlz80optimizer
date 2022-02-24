; Test case: 
; - line 5 (cp 1), should NOT be optimized, as the value of "a" is used later in function1 (defined in example5-include.asm)

	ld a, (value)
	cp 1
	call z, function1
	ld a, 2
	ld (value), a
__mdlrenamed__end:
	jr __mdlrenamed__end

function1:
	ld b, a
	ret

value:
	db 1
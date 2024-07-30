; Test case: 
; - line 5 (cp 1), should NOT be optimized, as the value of "a" is used in function 1

	ld a, (value)
	cp 1
	call z, function1
	ld a, b
	ld (value), a
__mdlrenamed__end:
	jr __mdlrenamed__end


function1:
	ld b, a
	ret


value:
	db 1
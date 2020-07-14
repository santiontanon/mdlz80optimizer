; Test case: 

ONE:	equ 1

	ld a,(value)
	cp ONE*0		; <-- should be optimized (we are just checking that even if there is an expression, since it evaluates to 0, it should match)
	call z,function1
	ld a,2
	ld (value),a
end:
	jp end


function1:
	ld a,0			; <-- should be optimized
	xor a
	ret


value:
	db 1
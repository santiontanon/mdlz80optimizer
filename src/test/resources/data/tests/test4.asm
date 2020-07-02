; Test case: 
; - line 5 (cp 1), should NOT be optimized, as the value of "a" is used later in cp 2

	ld a,(value)
	cp 1
	call z,function1
	cp 2
	call z,function2
	ld a,2
	ld (value),a
end:
	jp end


function1:
	ret

function2:
	ret


value:
	db 1
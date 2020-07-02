; Test case: 
; - line 6 (cp 0), should be optimized to "or a"
; - line 15 (ld a,0), should be removed

	ld a,(value)
	cp 0			; <-- should be optimized
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
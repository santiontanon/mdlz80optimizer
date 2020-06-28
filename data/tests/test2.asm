; Test case: 
; - line 6 (cp 1), should be optimized to "dec a"
; - line 14 (ld a,1), should be NOT optimized because of the "ret" (to be safe)

	ld a,(value)
	cp 1
	call z,function1
	ld a,2
	ld (value),a
end:
	jp end

function1:
	ld a,1
	ret


value:
	db 1
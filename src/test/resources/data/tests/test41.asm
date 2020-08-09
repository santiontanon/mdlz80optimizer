; Test case:
; - This test checks that callstack tracking works well, and that even 
;   if we go twice through the "wait_b_halts" code, when checking the 
;   dependencies of the "ld a,6" line, we should not stop checking, since
;   each time the call stack is different.

    ld a,6	; should not be optimized
loop1:
	call func1
    ld b,6
    call wait_b_halts
 	call func2
    ld b,6
    call wait_b_halts
    dec a
    jr nz,loop1

loop:
	jr loop


func1:
	di
	ld c,#05
	out (c),c
	ei
	ret

func2:
	di
	ld c,#04
	out (c),c
	ei
	ret

wait_b_halts:
    halt
    djnz wait_b_halts
    ret

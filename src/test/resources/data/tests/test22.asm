; Test case: a macro called before it's defined, inside another macro
; - This is to make sure the parser can handle it
; - also the ld a,1 of the first macro, should be optimized out

load_n_in_a1: macro ?n
	ld a,1
	load_n_in_a2 ?n
	endm


	load_n_in_a1 -2
	ld (var),a
end:
	jp end

var:
	db 0

load_n_in_a2: macro ?n
	IF ?n > 0
		ld a,?n
	ELSE
		ld a,-?n
	ENDIF
	endm

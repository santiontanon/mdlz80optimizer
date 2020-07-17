; Test case:

	ld a,1	; should be optimized (easy case)
	ld a,1		
	ld (val),a

	ld a,1	; should also be optimized (harder case)	
	ld (val2),a

end:
	jp end

val:
	db 0
val2:
	db 0
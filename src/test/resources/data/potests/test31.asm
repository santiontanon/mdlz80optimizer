; Test case:

	ld a,1	; should be optimized (easy case)
	ld a,1		
	ld (val),a

	ld a,1	; should also be optimized (harder case)	
	ld (val2),a

l2:	ld a,2	; this one should have the label kept!
	ld (val),a
	nop
	ld a,2	; this one should be optimized
	ld (val),a

	ld a,3
	ld (val),a
l3:	nop
	ld a,3	; this one should not be optimized because of the label
	ld (val),a

	ld a,4
	ld (val),a
	nop
l4:	ld a,4	; this one should not be optimized because of the label
	ld (val),a

	ld a,5
	ld (val),a
	nop
	ld a,5	; this one should be optimized
l5:	ld (val),a


end:
	jr end

val:
	db 0
val2:
	db 0

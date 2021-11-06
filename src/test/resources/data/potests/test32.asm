; Test case:

	ld b,0
	xor a
	ld (hl),0	; <-- this should be changed to ld (hl),b (easy case)
	add hl,bc

	ld bc,#ffff
	add hl,bc
	ld (hl),#ff	; <-- this should be optimzed to ld (hl),c (harder case)

	ld (val),a
	ld (val2),hl

	ld a,#ff	; <-- this should be optimized to ld a,c
	ld (val),a	

end:
	jp end

val:
	db 0
val2:
	dw 0
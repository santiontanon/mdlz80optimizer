; Test case:

	ld bc,#0000
	push bc	; should be removed
		ld a,(bc)
		ld (val),a
	pop bc	; should be removed

	ld bc,#0101
	push bc	; should be kept (due to the inc sp)
		ld a,(bc)
		ld (val),a
		inc sp
	pop bc	; should be kept (due to the inc sp)

	ld bc,#0202
	ld de,#0202
	push bc	; should be removed
	push de
		ld bc,#0303
		ld (val2),bc
		ld de,#0303
		ld (val2),de
	pop de
	pop bc	; should be removed

	ld (val2),de

	ld bc,#0404
	push bc	; should be kept
		ld bc,#0505
		ld (val2),bc
	pop bc	; should be kept
	ld (val2),bc

	ld ix,0
	push ix	; should be kept
		ld ixl,1
		ld a,(ix)
		ld (val),a
	pop ix	; should be kept
	ld a,(ix)
	ld (val),a

loop:
	jr loop

val:
	db 0
val2:
	dw 0
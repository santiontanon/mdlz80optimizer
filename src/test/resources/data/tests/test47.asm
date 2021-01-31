; test to check removal of repeated instruction:
	ld a, (ix+6)
	rlca
	rlca
	rlca
	rlca
	and a, #07
	and a, #07
	ld (val), a

loop:
	jr loop

val:
	db 0
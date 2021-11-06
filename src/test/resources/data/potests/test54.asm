; Test to see if -po-ldo does what it should:

	org #4000

label1:
	xor a
	ld hl, label1
	ld (hl), a
	ld hl, #4001  ; this line should be optimized (except if the -po-ldo flag is used)
	ld (hl), a

loop:
	jr loop
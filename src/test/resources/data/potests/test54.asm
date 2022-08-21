; Test to see if -po-ldo does what it should:

	org #4000

label1:
	ld hl, label1
	push hl
	ld hl, #4001  ; this line should be optimized (except if the -po-ldo flag is used)
	push hl

loop:
	jr loop
; A test to make sure unsafe optimizations for SDCC are not applied when the sdcc dialect is selected

	.org #4000

	ld hl, 10
	ld a,l
	ld hl, 20
	ld (hl), a

loop:
	jr loop
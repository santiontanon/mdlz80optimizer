; SDCC optimization test

	org #4000

label:
	ld c, a
	ld ix, 0xfff1
	ld (ix), c
	ld a, c

label2:
	ld (hl),a
	jr label2
	
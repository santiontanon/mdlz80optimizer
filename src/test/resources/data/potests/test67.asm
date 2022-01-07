; SDCC optimization test

	org #4000

label:
	ld c, a
	ld hl, 0xfff1
	ld (hl), c
	ld l, 0x12
	ld a, c

label2:
    ld h,0
	ld (hl),a
	jr label2
	
; SDCC optimization test
	org #4000
label:
    ld (65521), a
	ld l, #12
label2:
    ld h, 0
	ld (hl), a
	jr label2
	
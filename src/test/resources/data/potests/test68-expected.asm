; SDCC optimization test
	org #4000
label:
    ld (65521), a
label2:
	ld (hl), a
	jr label2
	
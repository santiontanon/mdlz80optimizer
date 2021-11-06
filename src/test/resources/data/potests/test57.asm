; Test: some test for removing useless code generated some times by SDCC

	ld	e, 0
label1:
	cp	a, a  ; these two are actually just a regular jump, so it should be replaced by just a "jr label2"
	jr	nc, label2
	inc	e
	ld (hl),e
label2:
	jr label2

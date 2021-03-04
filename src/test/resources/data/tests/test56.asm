; Test case: 

	srl a
	srl a
	srl a
	ld (hl),a
	inc hl

	ld (de),a
	srl a
	srl a
	srl a
	srl a
	ld (hl),a

loop:
	jr loop
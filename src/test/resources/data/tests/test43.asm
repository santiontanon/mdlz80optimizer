; test to check potential optimizations

EXPTBL:	equ $fcc1

	org	$4000

	ld	hl, EXPTBL
	inc	l
	inc	l
	inc	l
	inc	l
	ld	a, (hl)
	push	af
	inc	sp

	ld a,1
	ret
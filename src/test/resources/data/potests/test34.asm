; Test case:

loop:	ld a,a	; this label sohuld be kept, even if the op should be optimized!
		jr loop


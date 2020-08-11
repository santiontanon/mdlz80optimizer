; Test case:

	ld de,1	; this should be replaced to "ld e,1"
	ld (hl),e

	ld de,3	; this should not be optimized
	ld (val),de

end:
	jp end

val:
	dw 0
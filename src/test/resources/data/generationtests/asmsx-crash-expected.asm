; This test case used to make mdl crash when using the asmsx-zilog dialect
	org #4000
	db "AB", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	push af
	ld a, 1
	ld (#6000 + #1000), a
	pop af
	xor a
	ds #6000 - $, 0
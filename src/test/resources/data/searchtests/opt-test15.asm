    org #4000

main:
	nextreg 8, 00010010b
	ld a,0
	push af
	pop af
loop:
	jr loop

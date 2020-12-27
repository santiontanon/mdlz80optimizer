; Test case: 
	org #4000
    db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
loop:
	jr loop
	push af
	ld a, 1
	ld (#6000), a
	pop af
    ld a, 3
	ld (#7000), a
    ld b, 5
	push af
	ld a, b
	ld (#8000), a
	pop af	
	ds 8154, 0
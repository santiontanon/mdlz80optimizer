; Test case: 
	org #4000
    db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    ds ((#6000 / 8192) * 8192) - $, 0
    org (#6000 / 8192) * 8192
    ds 8192 - ($ - ((#6000 / 8192) * 8192)), 0
    org (#6000 / 8192) * 8192
loop:
	jp loop
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
	ds #8000 - $, 0
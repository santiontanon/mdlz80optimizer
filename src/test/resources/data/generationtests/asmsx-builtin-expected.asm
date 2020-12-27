; Test case: 
	org #4000
    db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
loop:
	jr loop
        ld a, 3
        ld b, 5
	ds 8170, 0
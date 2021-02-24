; Test case: 
	org #4000
    db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
loop:
	jp loop
	ds 8173, 0
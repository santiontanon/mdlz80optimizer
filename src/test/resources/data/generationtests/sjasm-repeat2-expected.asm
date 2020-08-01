; Test case: 
	db 0 * 10 + 0, 1
	db 0 * 10 + 1, 2
	db 1 * 10 + 0, 3
	db 1 * 10 + 1, 4
loop:
	jp loop


; Test case: 
	ld hl,(10)
	ld hl,[10]
	ld (var1),a
loop:
	jr loop

var1:
	db 0

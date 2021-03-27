; Test case (upcoming macro syntax for asMSX):
	org #4000
	db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	ld c, 5
	call 5
	ld c, 5
	call 5
	push hl
	push bc
	ld c, #ff
	ld hl, #ffff
	call FUNC
	pop bc
	pop hl	
loop:
	jr loop
FUNC:
	ret
	ds #6000 - $, 0

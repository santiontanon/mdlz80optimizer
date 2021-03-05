; Test case: 

	ld hl,var1
	ld de,var2
	ld a,(hl)	; should be replaced by ldi
	ld (de),a
	inc hl
	inc de

	ld a,1	; just to break the A dependency between the code above and below

	ld de,10	
	or a
	sbc hl,de	; should be replaced by add -10

	ld a,(hl)
	neg
	add a,10
	ld (var2),a

loop:
	jp loop

var1:
	db 0
var2:
	db 0
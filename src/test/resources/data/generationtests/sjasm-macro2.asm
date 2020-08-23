; Test case: 
	macro mymacro 2
	repeat @1
	ld a,low @2
	ld (var),a
	or a
    jr nz,1f
	ld a,high @2
	ld (var),a
1:	
	endrepeat
	endmacro

	mymacro 2, #0203

loop:
	jr loop

var:
	db 0

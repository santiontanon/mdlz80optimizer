; Test case: 
	macro mymacro 1..*
	repeat @0
	ld a,high @1
	ld (var),a
	or a
    jr nz,1f
	ld a,low @1
	ld (var),a
1:	
	rotate 1
	endrepeat
	endmacro

	mymacro #0102, #0304, #0506

loop:
	jr loop

var:
	db 0

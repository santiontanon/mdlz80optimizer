; Test case:

	ld ix,val
	ld b,(ix+1)
	ld c,(ix+2)
	ld (val2),bc

loop:
	jr loop

val:
	db 0,0,0
val2:
	dw 0
; Test to make sure moves do not happen if they will break jrs

	ld a, 1
	jp label1

label2:
	jr label2

	include "test4-file2.asm"

label3:
	jr label3

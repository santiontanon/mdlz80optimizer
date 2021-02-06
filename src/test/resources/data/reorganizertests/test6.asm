; Test when more than one block could be moved to the same position. To make sure only one is moved
loop:
	jr loop

loop2:
	jr loop2

label3:
	ld a, 1
	jp loop2

label1:
	ld b,(hl)
	jp label2

label2:
	ld b,(hl)
	jp label3

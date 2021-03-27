; Test case to make sure local labels are saved in a way asMSX can parse them afterwards
	org #8000
	db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
loop:
	call f1
	jp loop

f1:
	ld a, (hl)
	ld b, 8
f1_l1:
	inc a
	djnz f1_l1
	ld b, 8
f1_l2:
	inc a
	djnz f1_l2
	ld (hl), a
	ret
	ds #a000 - $, 0
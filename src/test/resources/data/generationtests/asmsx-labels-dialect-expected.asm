; Test case to make sure local labels are saved in a way asMSX can parse them afterwards
	org #8000
	.rom
	.start loop
loop:
	call f1
	jp loop

f1:
	ld a, [hl]
	ld b, 8
.l1:
	inc a
	djnz .l1
	ld b, 8
@@l2:
	inc a
	djnz @@l2
	ld [hl], a
	ret

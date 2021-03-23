; test to verify the creation of safety labels:

	or a
    jr z,$+5
    ld hl,1
    ld de,2

    ld (hl),0
    inc hl
    djnz $-3

loop:
	jr loop
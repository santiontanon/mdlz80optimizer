; test to verify the creation of safety labels:
	or a
    jr z, ___MDL_SAFETY_LABEL_1
    ld hl, 1
___MDL_SAFETY_LABEL_1:    ld de, 2
___MDL_SAFETY_LABEL_2:    ld (hl), 0
    inc hl
    djnz ___MDL_SAFETY_LABEL_2
loop:
	jr loop
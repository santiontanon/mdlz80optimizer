; SDCC optimization test

	org #4000

f1:
	ld l, (ix - 2)
	ld h, (ix - 1)
	add hl, hl
	add hl, hl
	ex de, hl
	push de
	ld hl, 10
	add hl, sp
	add hl, de
	pop de
	ld (ix - 18), l
	ld (ix - 17), h
f1_loop:
    jp f1_loop

f2:
	ld l, (ix - 2)
	ld h, (ix - 1)
	add hl, hl
	add hl, hl
    ld de, 10
    add hl, de
	ld (ix - 18), l
	ld (ix - 17), h
f2_loop:
    jp f2_loop

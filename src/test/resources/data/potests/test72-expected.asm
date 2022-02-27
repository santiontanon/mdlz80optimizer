; SDCC optimization test

	org #4000

part1:
    call f1
loop1:
    jp loop1

part2:
    call f2
loop2:
    jp loop2


f1:
	ld a, (ix - 14)
	ld (de), a
	inc de
	ld a, (ix - 13)
	ld (de), a
	ld e, (ix - 6)
	ld d, (ix - 5)
	inc de
	ld (ix - 8), e
	ld (ix - 7), d
	ld a, (ix - 14)
	ld (ix - 6), a
	ld a, (ix - 13)
	ld (ix - 5), a
	sla (ix - 6)
	rl (ix - 5)
	push de
	ld e, (ix - 14)
	ld d, (ix - 13)
	ld l, e
	ld h, d
	add hl, hl
	add hl, de
	pop de
    ret


f2:
	ld a, (ix - 14)
	ld (ix - 6), a
	ld (de), a
	inc de
	ld a, (ix - 13)
	ld (ix - 5), a
	ld (de), a
	ld e, (ix - 60)
	ld d, (ix - 50)
	inc de
	ld (ix - 8), e
	ld (ix - 7), d
	sla (ix - 6)
	rl (ix - 5)
	push de
	ld e, (ix - 14)
	ld d, (ix - 13)
	ld l, e
	ld h, d
	add hl, hl
	add hl, de
	pop de
    ret
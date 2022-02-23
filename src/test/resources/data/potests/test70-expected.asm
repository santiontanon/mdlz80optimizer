; SDCC optimization test
	org #4000
    ld h, 0
    ld a, (ix + 3)
    ld l, a
    ld bc, 10
    add hl, bc
    ld (hl), 2
    inc hl
    ld (hl), a
    ld h, 20
    ld l, (ix + 4)
    add hl, bc
    ld (ix + 4), l
    ld (hl), 3
    ld a, (ix + 4)  ; this should not be optimized
    inc hl
    ld (hl), a
loop:
    jp loop
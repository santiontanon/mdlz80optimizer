; SDCC optimization test
	org #4000
    ld a, (ix + 1)
    ld (ix + 2), a
    inc a
    ld (hl), a
    ld a, (ix + 2)
    add a, 10
    ld (ix + 2), a
    xor a
    ld (hl), a
    ld a, (ix + 2)  ; this should not be optimized
    ld (ix + 3), a
loop:
    jp loop
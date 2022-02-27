; SDCC optimization test

	org #4000

f1:
    ld a, (ix - 12)
    ld (ix - 16), a
    ld (ix - 8), a
    add a, (ix - 4)
    ld (ix - 6), a
    ld l, a
    ld a, (ix - 11)
    ld (ix - 15), a
    ld (ix - 7), a
    adc a, (ix - 3)
    ld (ix - 5), a
    ld h, a
    ld a, (hl)
    ret
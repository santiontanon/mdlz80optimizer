    org #4000

    ld hl, (#c004)
    add hl, de
    ld bc, (#c006)
    push hl
    xor a
    sbc hl, bc
    pop hl
    jp p, loop

    ld hl, 0
    ld a, (hl)
    ld (49152), hl
    ld (#c006), a

loop:
    jr loop
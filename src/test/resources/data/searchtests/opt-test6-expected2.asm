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
    ld (#c000), hl
    ld a, (hl)
    ld (#c006), a

loop:
    jr loop
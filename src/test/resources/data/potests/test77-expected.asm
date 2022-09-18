
    push ix
    ld ix, 0
    add ix, sp
    ld iy, -11
    add iy, sp
    ld sp, iy

    ld hl, _g_uDummy

    push iy
    ld a, (ix - 12)
    ld (hl), a
    pop iy

    ld sp, ix
    pop ix
    ret

_g_uDummy:

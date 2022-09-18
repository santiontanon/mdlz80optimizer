
    push    ix
    ld  ix,#0
    add ix,sp
    ld  iy, #-11
    add iy, sp
    ld  sp, iy

    push iy
    ld  hl, #_g_uDummy
    pop iy

    push    iy
    ld  a, -12 (ix)
    ld ( hl ), a
    pop iy

    ld  sp, ix
    pop ix
    ret

_g_uDummy:

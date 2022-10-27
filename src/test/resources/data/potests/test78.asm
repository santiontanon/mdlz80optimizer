
function:
    inc a
    ld (ix - 3), a
    sla (ix - 3)
    sla (ix - 3)
    ld a, (ix - 3)
    or a, (ix - 4)
    ld (ix - 3), a
    pop hl
    push hl
    ld a, (ix - 3)
    ld (hl), a
    ret

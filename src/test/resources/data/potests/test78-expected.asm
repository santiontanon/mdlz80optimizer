
function:
    inc a
    ld (ix - 3), a
    sla (ix - 3)
    sla (ix - 3)
    ld a, (ix - 3)
    or (ix - 4)
    ld (ix - 3), a
    ld (hl), a
    ret

    org #4000

    ld a, 0
    ld b, 0
    ld c, 1
    ld hl, var1
    add hl, bc
    ld (hl), a

loop:
    jr loop

    org #c000
var1: db 1

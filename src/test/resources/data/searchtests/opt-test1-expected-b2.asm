    org #4000

    xor a
    ld b, a
    ld c, 1
    ld hl, var1
    add hl, bc
    ld (hl), a

loop:
    jr loop

    org #c000
var1:    db 1

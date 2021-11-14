    org #4000

    ld bc, 1
    and b
    ld hl, var1
    inc l
    ld (hl), a

loop:
    jr loop

    org #c000
var1:    db 1

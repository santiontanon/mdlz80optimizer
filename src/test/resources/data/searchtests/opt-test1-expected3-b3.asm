    org #4000

    ld hl, var1
    and l
    inc l
    ld (hl), a

loop:
    jr loop

    org #c000
var1:    db 1
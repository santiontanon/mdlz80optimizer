    org #4000

    xor a
    ld c, (7 + 2) * 2
    ld b, c
    ld (bc), a

loop:
    jr loop

    org #c000
var1:    db 1

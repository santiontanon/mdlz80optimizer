    org #4000

    xor a
    ld b, (7 + 2) * 2
    ld c, b
    ld (bc), a

loop:
    jr loop

    org #c000
var1:    db 1

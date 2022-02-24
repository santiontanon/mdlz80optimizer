; Test: test for some SDCC-specific optimizations

    ld de, 10
    ld hl, (var2)
    add hl, de
    ld (var1), hl

    ld hl, (var4)
    add hl, de
    ld (var3), hl

    ld b, d
    xor a
    ld (bc), a

loop:
    jr loop

var1:
    dw 0
var2:
    dw 0
var3:
    dw 0
var4:
    dw 0

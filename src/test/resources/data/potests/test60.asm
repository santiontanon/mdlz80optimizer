; Test: test for some SDCC-specific optimizations

    ld hl, var1
    ld iy, var2
    ld a, (iy)
    add a, 10
    ld (hl), a
    ld a, (iy+1)
    adc a, 0
    inc hl
    ld (hl), a

    ld hl, var3
    ld iy, var4
    ld a, (iy)
    add a, 10
    ld (hl), a
    ld a, (iy+1)
    adc a, 0
    inc hl
    ld (hl), a

    ld b, 0
    ld a, 0
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

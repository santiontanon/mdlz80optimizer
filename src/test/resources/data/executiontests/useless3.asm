
    org #4000

start:
    ld b, 3
loop:
    ld c, 1
    ld a, b
    cp 2
    jr z, label1
    ld c, 2
label1:
    ld (hl), c
    djnz loop

end:
    jp end

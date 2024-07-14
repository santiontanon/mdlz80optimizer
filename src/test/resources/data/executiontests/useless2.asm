
    org #4000

start:
    ld a, 0
    push af
    ld b, a
    cp b
    cp 3
    jr z, label1
    ld a, 1
label1:
    pop af

end:
    jp end

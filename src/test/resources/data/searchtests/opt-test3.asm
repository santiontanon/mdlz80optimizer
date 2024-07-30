    org #4000

    call f1
    push af
loop:
    jr loop

f1:
    ld a, b
    or d
    or c
    jr nz, label1

    ld l, 0
    jr label2

label1:
    ld a, b
    cp c
    ret

label2:
    ld a, b
    or d
    or e
    ret

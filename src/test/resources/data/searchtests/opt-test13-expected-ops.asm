    org #4000

execute:
    ld de, (label1)
    ld d, 0
    ld a, (label2)
    push af
    push de
loop:
    jr loop

    org #c000
label1:
    org $ + 1
label2:
    org $ + 1

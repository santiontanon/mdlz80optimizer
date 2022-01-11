    org #4000

execute:
    ld a,(label1)
    ld e,a
    ld d,0
    ld a,(label2)
    push af
    push de
loop:
    jr loop

    org #c000
label1: ds virtual 1
label2: ds virtual 1

    org #4000

    ld a, #0f
    push af
    push hl
        ld bc, -64
        add hl, bc
        ex de, hl
    pop hl
    ld bc, 64
    add hl, bc
    ld (#c000), de
    ld (#c002), hl
loop:
    jr loop

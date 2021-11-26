    org #4000

execute:
    ld bc, #d000
    push bc

    ld hl, v1
    ld a, (hl)
    ld hl, v2
    add a, (hl)
    ld (v2), a

    ld a, (de)
    inc a
    ld (#d000), a

loop:
    jr loop

    org #c000
v1: ds virtual 1
v2: ds virtual 1

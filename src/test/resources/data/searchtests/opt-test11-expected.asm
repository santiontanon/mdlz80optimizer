    org #4000

execute:
    ld bc, #d000
    push bc
    ld a, (v1)
    ld hl, v2
    add a, (hl)
    ld (hl), a

    ld a, (de)
    inc a
    ld (bc), a

loop:
    jr loop

    org #c000
v1:
    org $ + 1
v2:
    org $ + 1

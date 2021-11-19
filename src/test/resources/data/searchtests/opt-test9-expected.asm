    org #4000

execute:
    ld de, v1
    ld hl, v2
    ld b, 0
    ld a, (v3)
    add a, a
    ld c, a
    rl b
    sla c
    rl b
    sla c
    rl b
    add hl, bc
    ld bc, 8
    ldir  

loop:
    jr loop

    org #cccc
v1:
    org $ + 2
v2:
    org $ + 2
v3:
    org $ + 1
    org #4000

execute:
    ld a, 5
    ld (v1), a

    ld iy, v2
    ld (iy + 0), 1
    ld (iy + 1), 2
    ld (iy + 2), 3
    ld (iy + 3), 4

    ld hl, v3
    ld (hl), 11
    inc l
    ld (hl), 12

loop:
    jr loop

    org #c000
v1:
    org $ + 1
v2:
    org $ + 4
v3:
    org $ + 2
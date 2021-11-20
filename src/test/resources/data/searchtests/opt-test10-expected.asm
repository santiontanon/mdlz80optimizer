    org #4000

execute:
    ld hl, v1
    ld (hl), 5

    ld iy, v2
    ld (iy + 0), 1
    ld (iy + 1), 2
    ld (iy + 2), 3
    ld (iy + 3), 4

    ld l, v3
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
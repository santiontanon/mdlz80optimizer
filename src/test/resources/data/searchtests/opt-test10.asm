    org #4000

execute:
    ld ix, v1
    ld (ix), 5

    ld iy, v2
    ld (iy+0), 1
    ld (iy+1), 2
    ld (iy+2), 3
    ld (iy+3), 4

    ld hl, v3
    ld (hl), 11
    ld hl, v3 + 1
    ld (hl), 12
loop:
    jr loop

    org #c000
v1: ds virtual 1
v2: ds virtual 4
v3: ds virtual 2

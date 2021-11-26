    org #4000

execute:
    ld (v1), hl
    ld hl, (v1)

loop:
    jr loop

    org #c000
v1: ds virtual 2

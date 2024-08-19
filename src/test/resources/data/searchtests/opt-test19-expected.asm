    org #4000

    ld hl, #1020
    ld (#8000), hl
    ld l, #30
    ld (#8002), hl
loop:
    jr loop

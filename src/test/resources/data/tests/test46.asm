; test to check the "bit 6,a" pattern
    bit 6,a
    jp nz,loop
    ld (hl), 1
loop:
    jr loop


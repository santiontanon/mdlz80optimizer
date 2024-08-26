; Test case:

start:
    ld a, ixl
    ld c, a
    ex de, hl
    ex de, hl
    ld (hl), c
loop:
    jr loop

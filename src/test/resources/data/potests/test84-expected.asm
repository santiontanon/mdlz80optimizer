; Test case:

start:
    ld c, ixl
    ld (hl), c
loop:
    jr loop

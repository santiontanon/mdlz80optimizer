; Test case:

start:
    ld (bc), a
    pop hl
    pop de
    pop bc
    ld (#c000), a
loop:
    jr loop

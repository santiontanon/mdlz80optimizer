; Test case:

start:
    ex de, hl
    ld (bc), a
    pop hl
    pop de
    ccf
    pop bc
    srl b
    ld (#c000), a
loop:
    jr loop

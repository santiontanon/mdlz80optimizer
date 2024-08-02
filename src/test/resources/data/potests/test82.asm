; Test case:

start:
    ld hl, #d000

    ld b, 1
    ld a, 0
    add a, b
    ld (#c000), a

    ld a, 10
    ld b, a
    ld c, b
    ld (hl), a
    inc hl
    ld (hl), c
loop:
    jr loop

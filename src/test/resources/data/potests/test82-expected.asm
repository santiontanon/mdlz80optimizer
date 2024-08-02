; Test case:

start:
    ld hl, #d000

    ld a, 1
    ld (#c000), a

    ld a, 10
    ld c, a
    ld (hl), a
    inc hl
    ld (hl), c
loop:
    jr loop

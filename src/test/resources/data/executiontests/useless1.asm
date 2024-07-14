
    org #4000

start:
    ld a, 0
    ld a, 1
    ld c, a
    add a, c
    ld (#c000), a
end:
    jp end

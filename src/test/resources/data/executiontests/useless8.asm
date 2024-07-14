
    org #4000

start:
    ld hl, 1
    ld de, 2
    ex de, hl
    ld (hl), 1
end:
    jp end



    org #4000

start:
    ld a, 1
    ld hl, #c000
    ld de, #c000
    exx
    ld (hl), a
    exx
    ld de, #c000
    ld (hl), a
    ld (de), a
end:
    jp end


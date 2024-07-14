
    org #4000

start:
    ld a, 1
    ld hl, #c000
    ex af, af'
    cp 2
    ex af, af'
    jp z, end
    ld (hl), a
end:
    jp end


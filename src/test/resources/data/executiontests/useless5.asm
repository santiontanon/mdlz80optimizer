
    org #4000

start:
    ld de, #c000
    inc e
    ld a, (de)
    ld (#c000), a
end:
    jp end


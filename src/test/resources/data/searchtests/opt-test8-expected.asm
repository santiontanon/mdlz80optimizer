    org #4000

execute:
    ld a, (shipstate)
    and a
    jp nz, shipcollided

loop:
    jr loop

shipcollided:
    jr shipcollided

    org #c000
shipstate:
    db 0
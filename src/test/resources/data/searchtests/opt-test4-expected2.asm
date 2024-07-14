    org #4000
    ld d, #0c
    ld hl, #c004
    ld e, l
    ld (#d001), a  ; two instructions in the middle, just so that "ld a, 4" and "ld e, 4"
    ld (#d002), a
    ld a, e
    inc e
    ld (#d003), a
    ld (#d004), a
    ldir
    ld (#d005), a
loop:
    jr loop
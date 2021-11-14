    org #4000

    ld d,#0c
    ld hl, #c004
    ld e, 4
    ld (#d001),a  ; two instructions in the middle, just so that "ld a, 4" and "ld e, 4"
    ld (#d002),a  ; are not together in a single optimization block.

    ld a, 4
    ld (#d003), a
    inc de
    ld a, 4
    ld (#d004), a

    ldir
    ld bc,0
    ld (#d005),a

loop:
    jr loop

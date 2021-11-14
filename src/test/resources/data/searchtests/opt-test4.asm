    org #4000

    ld d,#0c
    ld hl, #c004
    ld e, 4
    ld a,(hl)  ; two instructions in the middle, just so that "ld a, 4" and "ld e, 4"
    ld (de),a  ; are not together in a single optimization block.

    ld a, 4
    ld (de), a
    inc de
    ld a, 4
    ld (de), a

    ldir
    ld bc,0
    ld (bc),a

loop:
    jr loop

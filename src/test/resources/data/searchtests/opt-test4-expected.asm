    org #4000
    ld d, #0c
    ld hl, #c004
    ld e, l
    ld a,(hl)  ; two instructions in the middle, just so that "ld a, 4" and "ld e, 4"
    ld (de),a  ; are not together in a single optimization block.
    ld a, l
    ld (de), a
    inc de
    ld (de), a
    ldir
    ld (bc),a
loop:
    jr loop
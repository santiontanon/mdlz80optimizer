    ld hl,#4000
    ld b,2
label:
    ld a,(hl)
    add a,a
    ld (hl),a
    djnz label

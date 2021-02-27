__sjasm_page_0_start:
pletter._unpack:
    ld a, (hl)
    inc hl
    exx
    ld de, 0
    add a, a
    inc a
    rl e
    add a, a
    rl e
    add a, a
    rl e
    rl e
    ld hl, pletter._modes
    add hl, de
    ld e, (hl)
    db #dd, #6b  ; ld ixl,e
    inc hl
    ld e, (hl)
    db #dd, #63  ; ld ixh,e
    ld e, 1
    exx
    ld iy, pletter.loop
pletter.literal:
    ldi
pletter.loop:
    add a, a
    call z, pletter.getbit
    jr nc, pletter.literal
    exx
    ld h, d
    ld l, e
pletter.getlen:
    add a, a
    call z, pletter.getbitexx
    jr nc, pletter._lenok
pletter._lus:
    add a, a
    call z, pletter.getbitexx
    adc hl, hl
    ret c
    add a, a
    call z, pletter.getbitexx
    jr nc, pletter._lenok
    add a, a
    call z, pletter.getbitexx
    adc hl, hl
    ret c
    add a, a
    call z, pletter.getbitexx
    jp c, pletter._lus
pletter._lenok:
    inc hl
    exx
    ld c, (hl)
    inc hl
    ld b, 0
    bit 7, c
    jp z, pletter._offsok
    jp (ix)
pletter.mode7:
    add a, a
    call z, pletter.getbit
    rl b
pletter.mode6:
    add a, a
    call z, pletter.getbit
    rl b
pletter.mode5:
    add a, a
    call z, pletter.getbit
    rl b
pletter.mode4:
    add a, a
    call z, pletter.getbit
    rl b
pletter.mode3:
    add a, a
    call z, pletter.getbit
    rl b
pletter.mode2:
    add a, a
    call z, pletter.getbit
    rl b
    add a, a
    call z, pletter.getbit
    jr nc, pletter._offsok
    or a
    inc b
    res 7, c
pletter._offsok:
    inc bc
    push hl
    exx
    push hl
    exx
    ld l, e
    ld h, d
    sbc hl, bc
    pop bc
    ldir
    pop hl
    jp (iy)
pletter.getbit:
    ld a, (hl)
    inc hl
    rla
    ret
pletter.getbitexx:
    exx
    ld a, (hl)
    inc hl
    exx
    rla
    ret
pletter._modes:
    dw pletter._offsok
    dw pletter.mode2
    dw pletter.mode3
    dw pletter.mode4
    dw pletter.mode5
    dw pletter.mode6
    dw pletter.mode7
__sjasm_page_0_end:
    ld hl,#4000
    ld b,2
label:
    ld a,(hl)
    add a,a
    ld (hl),a
    djnz label
    call f1
    xor a
    call nz,f2
    call z,f2
    rst 0

f1:
    jp label2
label2:
    xor a
    jp nz,label3
    jp z,label3
label3:
    xor a
    ret nz
    ret z

f2:
    ld hl,#4000
    ld de,#4001
    ld bc,10
    ldir
    ld bc,4
    inc a
    cpdr
    ld b,4
    inir
    ld b,4
    otir
    ret
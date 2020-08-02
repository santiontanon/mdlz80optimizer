    module pletter
  
    MACRO MGBIT
    add a,a
    call z,getbit
    ENDM

    MACRO MGBITEXX
    add a,a
    call z,getbitexx
    ENDM
    
_unpack:
    ld a,(hl)
    inc hl
    exx
    ld de,0
    add a,a
    inc a
    rl e
    add a,a
    rl e
    add a,a
    rl e
    rl e
    ld hl,_modes
    add hl,de
    ld e,(hl)
    db 0xdd,0x6b ; ld ixl,e
    inc hl
    ld e,(hl)
    db 0xdd,0x63 ; ld ixh,e
    ld e,1
    exx
    ld iy,loop
literal:
    ldi
loop:
    MGBIT
    jr nc,literal
    exx
    ld h,d
    ld l,e
getlen:
    MGBITEXX
    jr nc,_lenok
_lus:
    MGBITEXX
    adc hl,hl
    ret c
    MGBITEXX
    jr nc,_lenok
    MGBITEXX
    adc hl,hl
    ret c
    MGBITEXX
    jp c,_lus
_lenok:
    inc hl
    exx
    ld c,(hl)
    inc hl
    ld b,0
    bit 7,c
    jp z,_offsok
    jp (ix)
    
mode7:
    MGBIT
    rl b
mode6:
    MGBIT
    rl b
mode5:
    MGBIT
    rl b
mode4:
    MGBIT
    rl b
mode3:
    MGBIT
    rl b
mode2:
    MGBIT
    rl b
    MGBIT
    jr nc,_offsok
    or a
    inc b
    res 7,c
_offsok:
    inc bc
    push hl
    exx
    push hl
    exx
    ld l,e
    ld h,d
    sbc hl,bc
    pop bc
    ldir
    pop hl
    jp (iy)
    
getbit:
    ld a,(hl)
    inc hl
    rla
    ret
    
getbitexx:
    exx
    ld a,(hl)
    inc hl
    exx
    rla
    ret
    
_modes:
  dw _offsok
  dw mode2
  dw mode3
  dw mode4
  dw mode5
  dw mode6
  dw mode7

    endmodule
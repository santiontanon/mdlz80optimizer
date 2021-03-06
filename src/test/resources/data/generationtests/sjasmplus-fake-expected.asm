    rl c
    rl b
    rl e
    rl d
    rl l
    rl h
    rr b
    rr c
    rr d
    rr e
    rr h
    rr l
    sla c
    rl b
    sla e
    rl d
    add hl, hl
    sli c
    rl b
    sli e
    rl d
    sli l
    rl h
    sli c
    rl b
    sli e
    rl d
    sli l
    rl h
    sra b
    rr c
    sra d
    rr e
    sra h
    rr l
    srl b
    rr c
    srl d
    rr e
    srl h
    rr l
    ld b, b
    ld c, c
    ld b, d
    ld c, e
    ld b, h
    ld c, l
    ld b, ixh
    ld c, ixl
    ld b, iyh
    ld c, iyl
    ld c, (hl)
    inc hl
    ld b, (hl)
    dec hl
    ld d, b
    ld e, c
    ld d, d
    ld e, e
    ld d, h
    ld e, l
    ld d, ixh
    ld e, ixl
    ld d, iyh
    ld e, iyl
    ld e, (hl)
    inc hl
    ld d, (hl)
    dec hl
    ld h, b
    ld l, c
    ld h, d
    ld l, e
    ld h, h
    ld l, l
    push ix
    pop hl
    push iy
    pop hl
    ld ixh, b
    ld ixl, c
    ld ixh, d
    ld ixl, e
    push hl
    pop ix
    ld ixh, ixh
    ld ixl, ixl
    push iy
    pop ix
    ld iyh, b
    ld iyl, c
    ld iyh, d
    ld iyl, e
    push hl
    pop iy
    push ix
    pop iy
    ld iyh, iyh
    ld iyl, iyl
    ld (hl), c
    inc hl
    ld (hl), b
    dec hl
    ld (hl), e
    inc hl
    ld (hl), d
    dec hl
    ld c, (hl)
    inc hl
    ld b, (hl)
    inc hl
    ld e, (hl)
    inc hl
    ld d, (hl)
    inc hl
    ld (hl), c
    inc hl
    ld (hl), b
    inc hl
    ld (hl), e
    inc hl
    ld (hl), d
    inc hl
    ld a, (bc)
    inc bc
    ld a, (de)
    inc de
    ld a, (hl)
    inc hl
    ld b, (hl)
    inc hl
    ld c, (hl)
    inc hl
    ld d, (hl)
    inc hl
    ld e, (hl)
    inc hl
    ld h, (hl)
    inc hl
    ld l, (hl)
    inc hl
    ld a, (bc)
    dec bc
    ld a, (de)
    dec de
    ld a, (hl)
    dec hl
    ld b, (hl)
    dec hl
    ld c, (hl)
    dec hl
    ld d, (hl)
    dec hl
    ld e, (hl)
    dec hl
    ld h, (hl)
    dec hl
    ld l, (hl)
    dec hl
    ld (bc), a
    inc bc
    ld (de), a
    inc de
    ld (hl), a
    inc hl
    ld (hl), b
    inc hl
    ld (hl), c
    inc hl
    ld (hl), d
    inc hl
    ld (hl), e
    inc hl
    ld (hl), h
    inc hl
    ld (hl), l
    inc hl
    ld (bc), a
    dec bc
    ld (de), a
    dec de
    ld (hl), a
    dec hl
    ld (hl), b
    dec hl
    ld (hl), c
    dec hl
    ld (hl), d
    dec hl
    ld (hl), e
    dec hl
    ld (hl), h
    dec hl
    ld (hl), l
    dec hl
    or a
    sbc hl, bc
    or a
    sbc hl, de
    or a
    sbc hl, hl
    or a
    sbc hl, sp
    ld c, (ix + 1)
    ld b, (ix + 1 + 1)
    ld c, (iy + 1)
    ld b, (iy + 1 + 1)
    ld e, (ix + 1)
    ld d, (ix + 1 + 1)
    ld e, (iy + 1)
    ld d, (iy + 1 + 1)
    ld l, (ix + 1)
    ld h, (ix + 1 + 1)
    ld l, (iy + 1)
    ld h, (iy + 1 + 1)
    ld (ix + 1), c
    ld (ix + 1 + 1), b
    ld (ix + 1), e
    ld (ix + 1 + 1), d
    ld (ix + 1), l
    ld (ix + 1 + 1), h
    ld (iy + 1), c
    ld (iy + 1 + 1), b
    ld (iy + 1), e
    ld (iy + 1 + 1), d
    ld (iy + 1), l
    ld (iy + 1 + 1), h
    ld c, (ix + 1)
    inc ix
    ld b, (ix + 1)
    inc ix
    ld c, (iy + 1)
    inc iy
    ld b, (iy + 1)
    inc iy
    ld e, (ix + 1)
    inc ix
    ld d, (ix + 1)
    inc ix
    ld e, (iy + 1)
    inc iy
    ld d, (iy + 1)
    inc iy
    ld l, (ix + 1)
    inc ix
    ld h, (ix + 1)
    inc ix
    ld l, (iy + 1)
    inc iy
    ld h, (iy + 1)
    inc iy
    ld (ix + 1), c
    inc ix
    ld (ix + 1), b
    inc ix
    ld (ix + 1), e
    inc ix
    ld (ix + 1), d
    inc ix
    ld (ix + 1), l
    inc ix
    ld (ix + 1), h
    inc ix
    ld (iy + 1), c
    inc iy
    ld (iy + 1), b
    inc iy
    ld (iy + 1), e
    inc iy
    ld (iy + 1), d
    inc iy
    ld (iy + 1), l
    inc iy
    ld (iy + 1), h
    inc iy
    ld a, (ix + 1)
    inc ix
    ld b, (ix + 1)
    inc ix
    ld c, (ix + 1)
    inc ix
    ld d, (ix + 1)
    inc ix
    ld e, (ix + 1)
    inc ix
    ld h, (ix + 1)
    inc ix
    ld l, (ix + 1)
    inc ix
    ld a, (iy + 1)
    inc iy
    ld b, (iy + 1)
    inc iy
    ld c, (iy + 1)
    inc iy
    ld d, (iy + 1)
    inc iy
    ld e, (iy + 1)
    inc iy
    ld h, (iy + 1)
    inc iy
    ld l, (iy + 1)
    inc iy
    ld a, (ix + 1)
    dec ix
    ld b, (ix + 1)
    dec ix
    ld c, (ix + 1)
    dec ix
    ld d, (ix + 1)
    dec ix
    ld e, (ix + 1)
    dec ix
    ld h, (ix + 1)
    dec ix
    ld l, (ix + 1)
    dec ix
    ld a, (iy + 1)
    dec iy
    ld b, (iy + 1)
    dec iy
    ld c, (iy + 1)
    dec iy
    ld d, (iy + 1)
    dec iy
    ld e, (iy + 1)
    dec iy
    ld h, (iy + 1)
    dec iy
    ld l, (iy + 1)
    dec iy
    ld (ix + 1), a
    inc ix
    ld (ix + 1), b
    inc ix
    ld (ix + 1), c
    inc ix
    ld (ix + 1), d
    inc ix
    ld (ix + 1), e
    inc ix
    ld (ix + 1), h
    inc ix
    ld (ix + 1), l
    inc ix
    ld (iy + 1), a
    inc iy
    ld (iy + 1), b
    inc iy
    ld (iy + 1), c
    inc iy
    ld (iy + 1), d
    inc iy
    ld (iy + 1), e
    inc iy
    ld (iy + 1), h
    inc iy
    ld (iy + 1), l
    inc iy
    ld (ix + 1), a
    dec ix
    ld (ix + 1), b
    dec ix
    ld (ix + 1), c
    dec ix
    ld (ix + 1), d
    dec ix
    ld (ix + 1), e
    dec ix
    ld (ix + 1), h
    dec ix
    ld (ix + 1), l
    dec ix
    ld (iy + 1), a
    dec iy
    ld (iy + 1), b
    dec iy
    ld (iy + 1), c
    dec iy
    ld (iy + 1), d
    dec iy
    ld (iy + 1), e
    dec iy
    ld (iy + 1), h
    dec iy
    ld (iy + 1), l
    dec iy
    ld (hl), 0
    inc hl
    ld (ix + 1), 0
    inc ix
    ld (iy + 1), 0
    inc iy
    ld (hl), 0
    dec hl
    ld (ix + 1), 0
    dec ix
    ld (iy + 1), 0
    dec iy
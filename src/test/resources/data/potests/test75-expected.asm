; SDCC optimization test

	org 0x4000

00112$:
;tmp/tests/md5/md5_c_Os.c:567: uint32_t _133 = *((&K.array[((int16_t)_112)]));
	ld l, (ix - 8)
;	spillPairReg hl
;	spillPairReg hl
	ld h, (ix - 7)
;	spillPairReg hl
;	spillPairReg hl
	add hl, hl
	add hl, hl
	ld (ix - 34), l
	ld (ix - 33), h
	ld a, _K & 0x00ff
	add a, (ix - 34)
	ld e, a
	ld a, (_K & 0xff00) >> 8
	adc a, (ix - 33)
	ld d, a
	ld hl, 32
	add hl, sp
	ex de, hl
	ld bc, 0x0004
	ldir
;tmp/tests/md5/md5_c_Os.c:568: uint32_t _134 = *((&_103[((int16_t)_117)]));
	ld e, (ix - 6)
	ld d, (ix - 5)
	ex de, hl
	add hl, hl
	add hl, hl
	ex de, hl
	ld l, (ix - 4)
	ld h, (ix - 3)
	add hl, de
	ex de, hl
	ld hl, 26
	add hl, sp
	ex de, hl
	ld c, 0x0004
	ldir
;tmp/tests/md5/md5_c_Os.c:112: uint32_t r = a + b;
	ld a, (ix - 28)
	add a, (ix - 12)
	ld c, a
	ld a, (ix - 27)
	adc a, (ix - 11)
	ld b, a
	ld a, (ix - 26)
	adc a, (ix - 10)
	ld e, a
	ld a, (ix - 25)
	adc a, (ix - 9)
	ld d, a
	ld a, c
	add a, (ix - 32)
	ld c, a
	ld a, b
	adc a, (ix - 31)
	ld b, a
	ld a, e
	adc a, (ix - 30)
	ld e, a
	ld a, d
	adc a, (ix - 29)
	ld d, a
	ld a, c
	add a, (ix - 38)
	ld c, a
	ld a, b
	adc a, (ix - 37)
	ld b, a
	ld a, e
	adc a, (ix - 36)
	ld e, a
	ld a, d
	adc a, (ix - 35)
	ld d, a
	ld (ix - 12), c
	ld (ix - 11), b
	ld (ix - 10), e
	ld (ix - 9), d
;tmp/tests/md5/md5_c_Os.c:570: uint32_t _136 = *((&S.array[((int16_t)_112)]));
	ld a, (ix - 34)
	add a, _S & 0x00ff
	ld e, a
	ld a, (ix - 33)
	adc a, (_S & 0xff00) >> 8
	ld d, a
	ld hl, 32
	add hl, sp
	ex de, hl
	ld bc, 0x0004
	ldir
    ret

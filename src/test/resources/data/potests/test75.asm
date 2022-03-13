; SDCC optimization test

	.org #0x4000

00112$:
;tmp/tests/md5/md5_c_Os.c:567: uint32_t _133 = *((&K.array[((int16_t)_112)]));
	ld l, -8 (ix)
;	spillPairReg hl
;	spillPairReg hl
	ld h, -7 (ix)
;	spillPairReg hl
;	spillPairReg hl
	add	hl, hl
	add	hl, hl
	ld -34 (ix), l
	ld -33 (ix), h
	ld a, #<(_K)
	add a, -34 (ix)
	ld e, a
	ld a, #>(_K)
	adc a, -33 (ix)
	ld d, a
	ld hl, #32
	add hl, sp
	ex de, hl
	ld bc, #0x0004
	ldir
;tmp/tests/md5/md5_c_Os.c:568: uint32_t _134 = *((&_103[((int16_t)_117)]));
	ld e, -6 (ix)
	ld d, -5 (ix)
	ex de, hl
	add hl, hl
	add hl, hl
	ex de, hl
	ld l, -4 (ix)
	ld h, -3 (ix)
	add hl, de
	ex de, hl
	ld hl, #26
	add hl, sp
	ex de, hl
	ld bc, #0x0004
	ldir
;tmp/tests/md5/md5_c_Os.c:112: uint32_t r = a + b;
	ld	a, -28 (ix)
	add	a, -12 (ix)
	ld	c, a
	ld	a, -27 (ix)
	adc	a, -11 (ix)
	ld	b, a
	ld	a, -26 (ix)
	adc	a, -10 (ix)
	ld	e, a
	ld	a, -25 (ix)
	adc	a, -9 (ix)
	ld	d, a
	ld	a, c
	add	a, -32 (ix)
	ld	c, a
	ld	a, b
	adc	a, -31 (ix)
	ld	b, a
	ld	a, e
	adc	a, -30 (ix)
	ld	e, a
	ld	a, d
	adc	a, -29 (ix)
	ld	d, a
	ld	a, c
	add	a, -38 (ix)
	ld	c, a
	ld	a, b
	adc	a, -37 (ix)
	ld	b, a
	ld	a, e
	adc	a, -36 (ix)
	ld	e, a
	ld	a, d
	adc	a, -35 (ix)
	ld	d, a
	ld	-12 (ix), c
	ld	-11 (ix), b
	ld	-10 (ix), e
	ld	-9 (ix), d
;tmp/tests/md5/md5_c_Os.c:570: uint32_t _136 = *((&S.array[((int16_t)_112)]));
	ld	a, -34 (ix)
	add	a, #<(_S)
	ld	e, a
	ld	a, -33 (ix)
	adc	a, #>(_S)
	ld	d, a
	ld	hl, #32
	add	hl, sp
	ex	de, hl
	ld	bc, #0x0004
	ldir
    ret

; SDCC optimization test
	org #4000
    inc (ix + 0)
    inc (ix + 0)
    ld a, (iy + 2)
    add a, 3  ; this should not be optimized
    ld (iy + 2), a	
loop:
    jp loop

; SDCC optimization test

	org #4000

    ld a, (ix)
    add a, 2  ; this should be optimized
    ld (ix), a	

    ld a, (iy + 2)
    add a, 3  ; this should not be optimized
    ld (iy + 2), a	
    
loop:
    jp loop

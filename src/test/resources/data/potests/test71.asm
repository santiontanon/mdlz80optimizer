; SDCC optimization test

	org #4000

    ld a,(ix+1)
    inc a
    ld (hl),a
    ld a,(ix+1)  ; this should be optimized
    ld (ix+2),a

    ld a,(ix+2)
    add a,10
    ld (ix+2),a
    xor a
    ld (hl),a
    ld a,(ix+2)  ; this should not be optimized
    ld (ix+3),a

loop:
    jp loop


; pattern: Move ld (?regpair + ?const2), a just before ld a, (?regixiy + ?const1), to save one of the ld a, (?regixiy + ?const1).
; 0: ld a, (?regixiy + ?const1)
; 1: *
; 2: ld a, (?regixiy + ?const1)
; 3: ld (?regixiy2 + ?const2), a
; replacement:
; 0: ld a, (?regixiy + ?const1)
; 3: ld (?regixiy2 + ?const2), a
; 1: *
; constraints:
; in(?regixiy,ix,iy)
; memoryNotWritten(1,?regixiy + ?const1)
; memoryNotUsed(1,?regixiy + ?const2)
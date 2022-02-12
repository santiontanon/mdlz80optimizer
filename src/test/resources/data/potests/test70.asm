; SDCC optimization test

	org #4000

    ld h,0
    ld l,(ix+3)
    ld bc,10
    add hl,bc
    ld (hl),2
    ld a,(ix+3)  ; this should be optimized
    inc hl
    ld (hl),a

    ld h,20
    ld l,(ix+4)
    add hl,bc
    ld (ix+4),l
    ld (hl),3
    ld a,(ix+4)  ; this should not be optimized
    inc hl
    ld (hl),a

loop:
    jp loop


; pattern: Prevent moving ?regpair to hl by using register a.
; 0: ld l, ?regpairl
; 1: ld h, ?regpairh
; 2: ld (hl), ?const1
; 3: inc hl
; 4: ld (hl), ?const2
; 5: inc ?regpair
; 6: inc ?regpair
; replacement:
; 0: a, ?const1
; 1: ld (?regpair), a
; 2: inc ?regpair
; 3: a, ?const2
; 4: ld (?regpair), a
; 6: inc ?regpair
; constraints:
; regpair(?regpair,?regpairh,?regpairl)
; regsNotUsedAfter(6,A)

; pattern: Move ld (?regpair + ?const2), a just before ld a, (?regixiy + ?const1), to save one of the ld a, (?regixiy + ?const1).
; 0: ld a, (?regixiy + ?const1)
; 1: *
; 2: ld a, (?regixiy + ?const1)
; 3: ld (?regpair + ?const2), a
; replacement:
; 0: ld a, (?regixiy + ?const1)
; 3: ld (?regpair + ?const2), a
; 1: *
; constraints:
; in(?regixiy,ix,iy)
; memoryNotWritten(1,?regixiy + ?const1)
; memoryNotUsed(1,?regixiy + ?const2)
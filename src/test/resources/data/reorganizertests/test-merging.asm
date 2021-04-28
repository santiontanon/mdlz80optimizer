; Making sure code blocks that look identical, but jump outside of themselves
; are not merged.

loop:
    jr loop

f1:
    jr z, f1sub
    or 1
f1skip:
    inc a
    ret
f1sub:
    ld a,2
    or a
    jr z,f1skip
    inc a
    ret
    
f2:
    jr z, f2sub
    or 2
f2skip:
    dec a
    ret
f2sub:
    ld a,2
    or a
    jr z,f2skip
    inc a
    ret

f3:
    ld a, 2
    dec a
    inc a
    ret    

f4:
    ld a, 2
    dec a
    inc a
    ret    
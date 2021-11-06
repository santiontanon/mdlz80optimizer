; Test case: 
; - only the last case (line 43) should be optimized

    ld bc,1
    ld hl,map
    jr z,label2
label1:
    ld b,2
    jr label3
label2:
    ld c,3
    jr label3
label3:
    add hl,bc
    ld (hl),a

    ld bc,1     ; only c is ever used, so, this should be optimized to ld c,1
    ld hl,map
    jr z,label2b
label1b:
    ld c,2
    jr label3b
label2b:
    ld a,3
    ld (map),a
    jr label3b
label3b:
    ld b,1
    add hl,bc
    ld (hl),a

    ld bc,1     ; only c is ever used, so, this should be optimized to ld c,1
    ld hl,map
    jr z,label2c
label1c:
    ld b,2
    jr label3c
label2c:
    ld bc,3
    jr label3c
label3c:
    add hl,bc
    ld (hl),a

    ld bc,1     ; <--- only this one should be optimized
    ld hl,map
    jr z,label2d
label1d:
    ld bc,2
    jr label3d
label2d:
    ld bc,3
    jr label3d
label3d:
    add hl,bc
    ld (hl),a

end:
    jp end

map:
    db 0
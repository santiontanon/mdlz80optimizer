; Test case: 
; - ld bc,1 should be optimized to write only c (as b is never used!)
; - ld bc,3 should be optimized
; - ld b,4; ld c,5 should be combined into one

    ld bc,1
    ld hl,map
    ld b,2
    add hl,bc
    ld (hl),a

    ld bc,3     ; <-- this one should be optimized out
    ld hl,map
    ld b,4      ; <-- these two should be combined into one
    ld c,5
    add hl,bc

    ld (hl),a

end:
    jp end

map:
    db 0
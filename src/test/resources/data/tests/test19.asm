; Test case: 

    ld a,b
    sub 4
    neg         ; <--- this should be optimized
    ld (var1),a
end:
    jp end

var1:
    db 0
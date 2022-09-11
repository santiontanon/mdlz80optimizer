    org #4000

f1:
    xor a
label1:
    ld (v1), a
    ret
    
f2:
label2:
    ld a, 1
    ld (v1), a
    ret

loop:
    jr loop

    org #c000
v1:
    org $ + 1
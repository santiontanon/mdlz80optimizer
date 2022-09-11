    org #4000

f1:
    xor a
    jp label1
label1:
    ld (v1), a
    ret
    
f2:
    jp label2
    xor a
label2:
    ld a, 1
    jp label1
    
loop:
    jr loop


    org #c000
v1: ds virtual 1
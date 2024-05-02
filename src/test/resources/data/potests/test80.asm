; Test case: 
; 'ld a, i' sets the z flag

f1:
    ld a, i
    jr z, label3
    jr label2

f2:
    ld a, i
    jr c, label3
    jr label2

label2:
    xor a
    ld a, (var1)
    ret

label3:
    xor a
    ld a, (var2)
    ret


var1:
    db 1

var2:
    db 2

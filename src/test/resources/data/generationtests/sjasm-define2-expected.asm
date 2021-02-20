; Test case: 
    db 1 * 1
    dw 0
    db 2 * 2
    dw 0
    db 3 * 3
    db 4 * 4, 5 + 5
    ld a, 15
    out (#0099), a
    ld a, #0087
    out (#0099), a
    db 1 + 1
  	db 1 + 1
  	db 2
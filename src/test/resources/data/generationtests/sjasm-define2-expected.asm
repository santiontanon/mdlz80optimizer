__sjasm_page_0_start:
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
    db (3) * 2
    db 1, 27, "J", 2
__sjasm_page_0_end:
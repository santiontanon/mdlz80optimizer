    org 0x0000
start:
    push af
    push bc
    push hl
    pop hl
    pop bc
    pop af
    nop
    nop
    nop
    outi
    outi
    outi
    outi
    outi
    outi
    outi
    outi
; Player effects handler routines
    ret
    db 0xff, 0xff  ; (padding)
    dw start + 14
    db "PUSH SPACE KEY", 0x00
__mdlrenamed__end:
    jr __mdlrenamed__end

    org #0100
strout: equ #09
bdos: equ #05
start:
autoexec:
    ld b, 3  ; 3 times
autoexec.loop:
    ld de, msg1
    ld c, strout
    push bc
    call bdos
    pop bc
    djnz autoexec.loop
    ld b, 3  ; 3 times
___expanded_macro___1.autoexec.loop:
    ld de, msg2
    ld c, strout
    push bc
    call bdos
    pop bc
    djnz ___expanded_macro___1.autoexec.loop
    jp 0
msg1:    db "First message: three times...", 13, 10, "$"
msg2:    db "Second message: three times...", 13, 10, "$"
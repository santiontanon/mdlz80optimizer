        OUTPUT test.com

        ORG 100h

strout  equ 09h
bdos    equ 05h

        macro print3t msg
                ld b, 3 ; 3 times
.loop
                ld de, msg
                ld c, strout
                push bc
                call bdos
                pop bc
                djnz .loop
        endm

start
autoexec
        print3t msg1
        print3t msg2
        jp 0

msg1    db "First message: three times...",13,10,"$"
msg2    db "Second message: three times...",13,10,"$"

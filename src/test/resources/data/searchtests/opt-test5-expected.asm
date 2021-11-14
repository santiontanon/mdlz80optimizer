TIMI: equ #fd9f
HKEY: equ #fd9a

    org #4000
    db "AB"
    dw Execute
    db 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
Execute:
    di
    im 1
    ld sp, #f380
    ld a, #c9
    ld (TIMI), a
    ld (HKEY), a
    ei

    ldir
    call f1
    xor a
    ld (#c000), a

loop:
    ld bc, #ffff
    jr loop

f1:
    ret
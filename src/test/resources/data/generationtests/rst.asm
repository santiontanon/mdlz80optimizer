    org #0000

    db 0,0,0,0,0,0,0,0

L0008:
    ret

loop:
    rst 8
    rst L0008
    jr loop

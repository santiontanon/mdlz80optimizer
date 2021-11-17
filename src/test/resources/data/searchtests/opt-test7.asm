    org #4000

    CheckIf60Hz:
        di
        in      a,(#99)
        nop
        nop
        nop
    vdpSync:
        in      a,(#99)
        and     #80
        jr      z,vdpSync

        ld      hl,#900
    vdpLoop:
        dec     hl
        ld      a,h
        or      l
        jr      nz,vdpLoop

        in      a,(#99)
        rlca
        and     1
        ei
        ret  
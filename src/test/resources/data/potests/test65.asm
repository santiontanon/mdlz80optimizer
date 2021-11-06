; Test: making sure MDL is aware that after an "ldir", bc == 0.


    ld hl,#c000
    ld de,#c800
    ld bc,#0880
    ldir

    ld bc,#000f  ; this should be optimized to ld c,#0f
    ld (#c000),bc

loop:
  jr loop
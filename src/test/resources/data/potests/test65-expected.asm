; Test: making sure MDL is aware that after an "ldir", bc == 0.
    ld hl, #c000
    ld de, #c800
    ld bc, #0880
    ldir
    ld c, #000f
    ld (#c000), bc
loop:
  jr loop
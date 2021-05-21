CONST: equ 0

  ld de, CONST
  add ix, de
  ld (ix), 0

  ld c, CONST
  add a, c
  ld (ix), a

loop:
  jr loop
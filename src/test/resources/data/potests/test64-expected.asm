; Test: making sure MDL is aware that register "r" can change value

  ld a, r
  ld h, a
  ld a, r  ; should NOT be optimized
  ld l, a
  ld (hl), 0

  ld a, 1
  ld h, a
  ld l, a
  ld (hl), 2


loop:
  jr loop
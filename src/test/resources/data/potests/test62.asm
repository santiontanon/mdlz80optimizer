; Test: some SDCC case

  ld a,(var1)
  ld (var2),a
  ld a,(var1+1)
  ld iy,var2
  ld (iy+1),a
  sra (iy+1)
  rr (iy)
  sra (iy+1)
  rr (iy)
  sra (iy+1)
  rr (iy)
loop:
  jr loop

var1: dw 0
var2: dw 0
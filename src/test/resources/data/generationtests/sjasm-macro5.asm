; Test case: 
macro m1 n
[2] rrca
  ld (hl),a
[n] rrca
  ld (bc),a
endmacro

  m1 3

loop:
  jr loop


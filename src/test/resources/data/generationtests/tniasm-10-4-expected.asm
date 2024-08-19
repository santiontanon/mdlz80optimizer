  org 0x8000
  dw 0x4000, ___expanded_macro___1..#len, 1024, 0x00
___expanded_macro___1..#start:
  db "test string"
___expanded_macro___1..#len: equ $ - ___expanded_macro___1..#start

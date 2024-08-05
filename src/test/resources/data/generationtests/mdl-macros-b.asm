  org #0000

m2: macro
jorl:
  nop
endm


m1: macro A
  m2
IF A == 0
  ld a, 1
ELSE
  ld a, 2
  jp label2
ENDIF
label1:
  jp label1
IF A != 0
label2:
  jp label2
ENDIF
endm

  m1 0
  m1 1
  m1 2

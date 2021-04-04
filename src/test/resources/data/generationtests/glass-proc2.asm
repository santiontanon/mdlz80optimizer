; Thanks to ToriHino for this test case (which failed in version 1.8.1)
CONST: PROC

CHARS: PROC
OUTSIDE equ 0
   ENDP

LEFT_X1  equ 5 

  REPT LEFT_X1 - 1
  db CHARS.OUTSIDE
  ENDM

  ENDP

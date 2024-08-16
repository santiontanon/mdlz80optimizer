  org $8000

VAR1: %equ 1

label1:
%IF VAR1 = 1
  nop
%ENDIF
  jr label1

  org $8000

VAR1: %equ 1

10_label1:
%IF VAR1 = 1
  nop
%ENDIF
  jr 10_label1

.20_sublabel2:
  nop
  jr .20_sublabel2

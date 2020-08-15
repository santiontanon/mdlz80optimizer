; Test case: 
include_label: equ $
label:  ; this was inside of the include
	nop
loop:
	jr loop
__mdlrenamed__end:
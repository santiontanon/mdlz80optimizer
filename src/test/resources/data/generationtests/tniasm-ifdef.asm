; Test case: 
CONSTANT: equ 0
label1:
	nop
.local1:
IFDEF CONSTANT
.local2:
	nop
.local3:
	nop
ELSE
.local4:
	nop
.local5:
	nop
ENDIF
label2:
	nop
loop:
	jp loop

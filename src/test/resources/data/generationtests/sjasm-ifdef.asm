; Test case: 
CONSTANT: equ 0
label1:
	nop
.local1:
1:
	nop
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

; Test case: 

proc_label1: PROC
local1:
	nop
	ENDP

proc_label2: PROC
local2:
	nop
proc_label3: PROC
local3:
	nop
	ENDP
	ENDP

loop:
	jr loop
value:
	db 0

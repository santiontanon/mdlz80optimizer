; Test case: 

classA: macro
labelA1:
	db 0
	endm

classB: macro
super: classA
	proc_label1: PROC
	local1:
		jr local1
	ENDP
	endm

instantiation1: classB

loop:
	jr loop

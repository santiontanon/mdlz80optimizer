; Test case: 
proc_label1:
proc_label1.local1:
	nop
proc_label2:
proc_label2.local2:
	nop
proc_label2.proc_label3:
proc_label2.proc_label3.local3:
	nop
loop:
	jr loop
value:
	db 0

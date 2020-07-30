classB.proc_label1.local1: equ 1
classB.proc_label1: equ 1
classB.super.labelA1: equ 0
classB.super: equ 3
classA.labelA1: equ 0
; Test case: 
instantiation1:
instantiation1.super:
instantiation1.super.labelA1:
	db 0
instantiation1.proc_label1:
instantiation1.proc_label1.local1:
	jr instantiation1.proc_label1.local1
loop:
	jr loop

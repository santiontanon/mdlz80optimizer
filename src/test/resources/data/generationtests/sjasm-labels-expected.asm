__sjasm_page_0_start:
; Test case: 
label1:
	jp loop
label2:    jp loop
label3:    nop
label4:
	jp loop
label5:    jp loop
label6:    nop
loop:
	jp loop
var1: equ #c000
var2: equ #c001
mystruct: equ 4
mystruct.x1: equ 0
mystruct.x2: equ 1
mystruct.x3: equ 3
var3: equ #c002
var4: equ #c003
var5: equ #c004
var6: equ #c005
__sjasm_page_0_end:
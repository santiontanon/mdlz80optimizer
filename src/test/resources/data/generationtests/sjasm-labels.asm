; Test case: 
label1:
	jp loop

label2: jp loop

label3: nop

label4
	jp loop

label5 jp loop

label6 nop

loop:
	jp loop

	map 0xC000
var1:	#	1
var2	#	1
	struct mystruct
x1:		db 0
x2		dw 0
x3:		db 0
	ends
var3:	#1
var4	#1
var5	#0x1
var6	##1
	endmap

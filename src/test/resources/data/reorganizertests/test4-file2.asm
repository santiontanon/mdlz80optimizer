label6:
	REPT 128
	nop
	ENDM

label1:
	add a,b
	jr nc,label3
	jp label2

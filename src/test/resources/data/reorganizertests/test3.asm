; Test to make sure moves do not happen if they will break jrs

	ld a, 1
	jp label1

label2:
	jr nc,label4
	jr label2

label4:
	jr label4
label6:
	REPT 128
	nop
	ENDM

label5:
	jr label5

label1:
	add a,b
	jr nc,label3
	jp label2

label3:
	jr label3

	org 100H

RAM_PAGE0: ds 3F00H	
RAM_PAGE1: ds 4000H

TPA_PAGE0: equ RAM_PAGE0
TPA_PAGE1: equ RAM_PAGE1
RAM: equ RAM_PAGE1


Application: MACRO
	frame:
		dw 0
	image:
		dw 0
	altImage:
		dw 0
	ENDM

	SECTION TPA_PAGE0
	
	ld ix,Application_instance
	push ix

loop:
	jr loop

	ENDS

	SECTION RAM

Application_instance: Application

	ENDS

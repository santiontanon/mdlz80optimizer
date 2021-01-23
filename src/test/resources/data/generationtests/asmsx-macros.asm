; Test case (upcoming macro syntax for asMSX):
	org #4000
	.rom
    .start loop

	macro BDOS1 #fun       ; macro definition
	ld c,#fun
	call 5
	endmacro

BDOS2: macro @fun
	ld c, @fun
	call 5
endm

m_SOUND: MACRO #PAR1, #PAR2
	push	HL
	push	BC
	ld	C,#PAR1
	ld	HL,#PAR2
	call	FUNC
	pop	BC
	pop	HL
ENDM	

	BDOS1 5               ; macro call 1
	BDOS2 5               ; macro call 2
	m_SOUND #ff, #ffff

loop:
	jr loop

FUNC:
	ret
; Test case: 
CONSTANT: equ 0
label1:
	nop
.local1:
1:
	nop
IFDEF CONSTANT
.local2:
	nop
.local3:
	nop
ELSE
.local4:
	nop
.local5:
	nop
ENDIF
label2:
	nop
loop:
	jp loop

	; Set VDP for write (based on DE or HL)
	macro SETWRT reg
	ifdifi reg,de
		ld a,l
		di
		out ($99),a
		ld a,h
	else
		ld a,e
		di
		out ($99),a
		ld a,d
	endif
		or $40
		out ($99),a
		ei		
	endmacro
	SETWRT de
	SETWRT hl
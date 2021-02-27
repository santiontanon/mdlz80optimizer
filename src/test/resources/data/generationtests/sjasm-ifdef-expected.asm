__sjasm_page_0_start: equ $
; Test case: 
CONSTANT: equ 0
label1:
	nop
label1.local1:
_sjasm_reusable_1_1:
	nop
label1.local2:
	nop
label1.local3:
	nop
label2:
	nop
loop:
	jp loop
	; Set VDP for write (based on DE or HL)
	ld a, e
	di
	out (#99), a
	ld a, d
	or #40
	out (#99), a
	ei		
	ld a, l
	di
	out (#99), a
	ld a, h
	or #40
	out (#99), a
	ei		
__sjasm_page_0_end:
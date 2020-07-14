; Test case: for some reason macros within REPTs had funny behavior

ld_val: macro ?par1
	ld a,?par1
	ld (val),a
	endm

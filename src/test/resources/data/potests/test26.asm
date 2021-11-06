; Test case: for some reason macros within REPTs had funny behavior

	REPT 4
	ld a,1
	jumpmacro
	ENDM	
	
end:
	jp end

jumpmacro: macro
	jp ix
	endm

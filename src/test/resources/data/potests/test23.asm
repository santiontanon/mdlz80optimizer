; Test case: making sure pre-processor parses code correctly (this case didn't work in an early version)


IFDEF symbol
	ld b,1
	ld c,2
ELSE
	ld b,3
	ld c,4
ENDIF
	ld (symbol),bc

end:
	jp end

symbol:
	dw 0

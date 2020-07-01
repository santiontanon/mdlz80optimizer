; Test case: 

	ld bc,1		; <-- should not be optimized, as we don't know what undefinedSymbol does
	call undefinedSymbol

	ld bc,2		; <-- should be optimized
	call definedSymbol

end:
	jp end

definedSymbol:
	ld bc,2
	ret
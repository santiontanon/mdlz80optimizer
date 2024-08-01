; Test case: 

	ld bc,1		; <-- should not be optimized, as we don't know what undefinedSymbol does
	call undefinedSymbol

	ld bc,2		; <-- should be optimized
	call definedSymbol

	ld bc,3		; <-- should be optimized
	call definedSymbol2
        ld (#c000), bc
        
end:
	jp end

definedSymbol:
	ld bc,4		; <-- should be optimized
	ret

definedSymbol2:
	ld bc,5
	ret
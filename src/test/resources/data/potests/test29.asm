; Test case: test to see if MDL can track the file names / line numbers properly 
;            when optimizing code form macros defined in another file.

	include "test29-include.asm"

	ld_val 0	
	ld_val 1
	ld_val 0
	
end:
	jp end

val:
	db 0
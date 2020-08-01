; Test case: 
	macro	mymacro n
	if (n<=4)
[n]	RRCA
	else
[8-n] RLCA
	endif
	endmacro

	mymacro 5
loop:
	jp loop


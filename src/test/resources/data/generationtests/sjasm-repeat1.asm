; Test case: 
	repeat 2
	repeat 2
	db	@@# * 10 + @#
	endrepeat
	endrepeat
loop:
	jp loop


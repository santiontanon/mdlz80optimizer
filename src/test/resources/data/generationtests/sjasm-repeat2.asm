; Test case: 
counter:=1
	repeat 2
	repeat 2
	db	@@# * 10 + @#, counter
counter:=counter+1
	endrepeat
	endrepeat
loop:
	jp loop


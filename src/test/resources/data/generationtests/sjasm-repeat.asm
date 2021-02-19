scorebar_sat:

counter:=32*4
	repeat	6
	repeat	4
	db	@@# * 8 -8-1
	if	(!((@@#/4) & 1))
		if	((counter!=32*4) & (counter!=32*4+4*4) & (counter!=4*8+32*4) & (counter!=4*12+32*4))
			db	(@# * 16)+192
		else
			db	0
		endif
	else
		db	96+(@# * 16)
	endif
	db	0+counter
	db	14+8-counter/16
counter:=counter+4
	endrepeat
	endrepeat

	repeat	4
	db	3*16
	db	96+(@# * 16)
	db	counter
	db	15
counter:=counter+4
	endrepeat
	db	64-2
	db	0
	db	0x98
	db	0

	db	0xd0
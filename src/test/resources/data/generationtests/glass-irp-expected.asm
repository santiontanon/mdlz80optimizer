; Test case: 
	xor a
	or 1
	or 2
	or 4
	or 8
	ld (value), a
end:
	jp end
value:
	db 0

; Test case: 
	xor a
IRP ?value, 1 , 2, 4, 8
	or ?value
ENDM
	ld (value),a
end:
	jp end
value:
	db 0

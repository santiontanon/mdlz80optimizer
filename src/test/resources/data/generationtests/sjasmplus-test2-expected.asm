; Test case: evaluation of "$" inside macros and eager variables
	org #2000
	db 8192
	db 8193
	db $
	org #4000
loop:
	jr loop

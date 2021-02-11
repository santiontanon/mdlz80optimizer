; Test case: evaluation of "$" inside macros and eager variables
	macro m1
variable = $
	db variable
variable = variable + 1
	db variable
	endm

	macro m2
	db $
	endm

	org #2000

	m1

	m2

	org #4000

loop:
	jr loop

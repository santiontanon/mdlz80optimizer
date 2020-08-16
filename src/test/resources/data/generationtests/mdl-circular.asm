; Test case: 
; this case is tricky, since there is a circular dependency between START, CODE_END and CODE_START

START:  equ #8000 - (CODE_END - CODE_START)

	org START

CODE_START:
loop:
	jr loop
CODE_END:

	db START

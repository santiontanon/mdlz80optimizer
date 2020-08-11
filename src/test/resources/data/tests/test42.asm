; Test case: to make sure dependencies for the daa, rld and rrd are tracked properly

	ld	a,(value)
	add	a,00h	; should not be optimized
	daa		

	ld (value),a
	ld hl,value2	; should not be optimized
	rld
	ld hl,value3	; should be optimized to inc hl
	rrd

loop:
	jr loop

value:
	db 0
value2:
	db 0
value3:
	db 0
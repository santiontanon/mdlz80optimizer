; test to check that the order of push/pops is taken into account

	org #4000

function1:
	ld b, 10
	push de
	ld de, variable
	ld (de), a
	pop de
	ld  (hl), b	; this should be optimized
	ld b, 2
	ret

function2:
	ld b, 20
	pop hl
	push hl
	ld (hl), b ; this should not be optimized
	ld b, 2
	ret

	org #c000
variable: ds virtual 1
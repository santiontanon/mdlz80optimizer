; Test case: 
EXTERNAL: equ #0000
	org #4000
	ld a, (value)
	dec a
    scf
	call z, function1
	ld a, 2
	ld (value), a

	ld b, 3  ; since we don't know what "EXTERNAL" does, we cannot optimize this
	call EXTERNAL
__mdlrenamed__end:
    jr __mdlrenamed__end
function1:
    ld a, 1  ; should be NOT optimized because of the "ret" (to be safe)
	ret
value:
	db 1
; Test of the function inlining feature
	org #4000
	ld bc, var
	; calling func1 (this should be inlined)
;     call func1  ; -mdl
func1:
	ld a, (bc)
;     ret  ; -mdl
	ld hl, var
	ld (hl), a
	; calling func2 (this should not be inlined, since it's called twice)
	call func2
	; calling func3 (this should not be inlined either,
	; since there is a dependency with the func3a label)
	call func3
	; this should not be inlined, as func4 has a jp internally
	call func4	
loop:
	call func2
	call func3a
	jr loop
func2:
	ld a, (de)
	ret
func3:
	ld a, (hl)
func3a:
	inc a
	ret
func4:
	ld a, (ix)
	or a
	jp z, func3a
	ret
	org #c000
var:
    org $ + 1
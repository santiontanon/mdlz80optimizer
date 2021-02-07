; Test when more than one block could be moved to the same position. To make sure only one is moved
loop:
	jr loop
func2:
	ld a, 1
; 	jp func1  ; -mdl
func1:
	ld (hl), a
	ret
func3:
	ld a, 2
	jp func1
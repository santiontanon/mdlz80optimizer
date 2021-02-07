; Test when more than one block could be moved to the same position. To make sure only one is moved
loop:
	jr loop
label1:
	ld b, (hl)
; 	jp label2  ; -mdl
label2:
	ld b, (hl)
; 	jp label3  ; -mdl
label3:
	ld a, 1
; 	jp loop2  ; -mdl
loop2:
	jr loop2
; Test to see if we can protect areas from optimization:
	ld a, 1  ; mdl:no-opt-start
	jp label1  ; mdl:no-opt-end
label1:
	add a, b
; 	jp label2  ; -mdl	
label2:
	ld (hl), a
label3:
	jr label3
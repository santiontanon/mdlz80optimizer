; Making sure the optimizer respects the "mdl:no-opt" directive
	ld a, 1
	jp label1  ; mdl:no-opt
label1:
	add a, b
; 	jp label2  ; -mdl
label2:
	ld (hl), a
label3:
	jr label3
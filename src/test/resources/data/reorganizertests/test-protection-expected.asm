; Test to see if we can protect areas from optimization:
	ld a, 1  ; mdl:no-opt-start
	jp label1
label2:
	ld (hl), a  ; mdl:no-opt-end
label3:
	jr label3
label1:
	add a, b
	jp label2
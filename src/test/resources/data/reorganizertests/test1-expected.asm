; Basic functioning test, there should be two reorganizations:
; - bringing label1 to just before label2.
; - then, the "jp label2" followed by "label2" should result in removing the "jp label2"
	ld a, 1
; jp label1  ; -mdl
label1:
	add a, b
; jp label2  ; -mdl
label2:
	ld (hl), a
label3:
	jr label3
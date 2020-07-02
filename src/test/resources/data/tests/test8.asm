; Test case: 
; - line 8 (ld a,2), should be optimized

label1:
	ld a,1
	push af
		ld (var1),a
		ld a,2
	pop af
	ld (var1),a
label2:
	jp label2

var1:
	db 0
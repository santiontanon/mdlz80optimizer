; Test case: 
; - line 5 (ld a,1), should be optimized

label1:
	ld a,1
	ld a,2
	ld (var1),a
label2:
	jp label2

var1:
	db 0
; Test case: 
; - line 8 (ld a,2), should be optimized

label1:
	ld a, 1
    ld (var1), a
	ld (var1), a
label2:
	jr label2

var1:
	db 0
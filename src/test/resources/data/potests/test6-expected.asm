; Test case: 
; - line 19 (cp 1), should be optimized
; - line 26 (ld e,a), should be optimized

label1:
	ld hl, var1
	ld a, (hl)
	or a
	jr z, label4
	dec (hl)
	jr label4

label2:
	ld hl, var1
	ld a, (hl)
	or a
	jr nz, label3
	ld a, (var1)
	dec a
	jr z, label1

	inc (hl)
label3:
	dec (hl)
label4:
	jr label4


var1:
	db 0
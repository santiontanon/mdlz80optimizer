; Test case: 
; - line 19 (cp 1), should be optimized
; - line 26 (ld e,a), should be optimized

label1:
	ld hl,var1
	ld a,(hl)
	or a
	jp z,label4
	dec (hl)
	jr label4

label2:
	ld hl,var1
	ld a,(hl)
	or a
	jr nz,label3
	ld a,(var1)
	cp 1	; should be optimized
	jr z,label1

	inc (hl)
label3:
	dec (hl)
	ld a,(var1)	; should be optimized (as a consequence of optimizing the one below)
	ld e,a	; should be optimized
label4:
	jp label4


var1:
	db 0
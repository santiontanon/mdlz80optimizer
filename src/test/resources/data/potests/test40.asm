; Test case:

	inc l	; this sequence if inc l should not be optimized (due to the labels)
	inc l
	inc l
label1:
	inc l
	inc l
	inc l
label2:
	inc l
	inc l
	ld (hl),0


	inc l	; this sequence if inc l should be optimized
	inc l
	inc l
	inc l
	inc l
	inc l
	inc l
	inc l
	ld (hl),1

	dec iy	; this sequence of inc iy should be optimized 
	dec iy	; and two patterns should actually match:
	dec iy	; one that replaces by push bc; ld bc,5; add iy,bc; pop bc
	dec iy  ; and then a second that notices bc is not used lter, and removes push/pop
	dec iy
label3:		; this label should not matter
	ld a,(iy)
	ld (var),a

loop:
	jr loop

var:
	db 0

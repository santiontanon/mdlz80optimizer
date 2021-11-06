; Test: corner case found by Bengalack

	ld hl,variable  ; this block should be optimized
	ld c,(hl)
	inc hl
	ld b,(hl)
	ld a,(bc)
	ld (variable3),a

	ld hl,(variable)  ; this block should not be optimized
	ld c,(hl)
	inc hl
	ld b,(hl)
	ld a,(bc)
	ld (variable3),a

loop:
	jr loop

variable: db #00
variable2: dw #0000
variable3: db #00
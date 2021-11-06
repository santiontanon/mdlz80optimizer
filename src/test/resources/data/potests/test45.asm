; test to check some new patterns
	ld hl,var
	ld e,(hl)
	inc hl
	ld d,(hl)
	ld (var2),de

	ld hl,va2
	ld c,1
	halt  ; just some instruction in between
	ld b,2
	add hl,bc
	ld (var2),hl

	ld c,10
	ld a,20
	ld de,30
	ld iyl,40
	ld b,50
	call f

loop:
	jr loop

f:  ; just use all the registers in some random way
	ld (var),a
	ex de,hl
	add hl,bc
	ld (var2),hl
	ld a,iyl
	ld (var),a
	ret

var:
	db 0
var2:
	dw 0


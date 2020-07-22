; Test case: 
	ld a, 1
	ld (var), a
	or a
	jr nz, _sjasm_reusable_1_4
	ld a, 2
	ld (var), a
_sjasm_reusable_1_4:
	ld a, 3
	ld (var), a
	or a
	jr nz, _sjasm_reusable_1_5
	ld a, 4
	ld (var), a
_sjasm_reusable_1_5:
	ld a, 5
	ld (var), a
	or a
	jr nz, _sjasm_reusable_1_6
	ld a, 6
	ld (var), a
_sjasm_reusable_1_6:
loop:
	jr loop
var:
	db 0
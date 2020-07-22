; Test case: 
	ld a, 3
	ld (var), a
	or a
    jr nz, _sjasm_reusable_1_2
	ld a, 2
	ld (var), a
_sjasm_reusable_1_2:
	ld a, 3
	ld (var), a
	or a
    jr nz, _sjasm_reusable_1_3
	ld a, 2
	ld (var), a
_sjasm_reusable_1_3:
loop:
	jr loop
var:
	db 0

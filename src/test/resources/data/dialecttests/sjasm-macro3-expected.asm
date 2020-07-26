; Test case: 
	ld a, (258 & #ff00) >> 8
	ld (var), a
	or a
	jr nz, _sjasm_reusable_1_4
	ld a, 258 & #00ff
	ld (var), a
_sjasm_reusable_1_4:
	ld a, (772 & #ff00) >> 8
	ld (var), a
	or a
	jr nz, _sjasm_reusable_1_5
	ld a, 772 & #00ff
	ld (var), a
_sjasm_reusable_1_5:
	ld a, (1286 & #ff00) >> 8
	ld (var), a
	or a
	jr nz, _sjasm_reusable_1_6
	ld a, 1286 & #00ff
	ld (var), a
_sjasm_reusable_1_6:
loop:
	jr loop
var:
	db 0
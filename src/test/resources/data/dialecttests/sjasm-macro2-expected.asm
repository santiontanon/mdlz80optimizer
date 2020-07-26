; Test case: 
	ld a, 515 & #00ff
	ld (var), a
	or a
    jr nz, _sjasm_reusable_1_2
	ld a, (515 & #ff00) >> 8
	ld (var), a
_sjasm_reusable_1_2:
	ld a, 515 & #00ff
	ld (var), a
	or a
    jr nz, _sjasm_reusable_1_3
	ld a, (515 & #ff00) >> 8
	ld (var), a
_sjasm_reusable_1_3:
loop:
	jr loop
var:
	db 0

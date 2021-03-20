__sjasm_page_0_start:
; Test case: 
	ld a, #0203 & #00ff
	ld (var), a
	or a
    jr nz, ___expanded_macro___1._sjasm_reusable_1_1
	ld a, (#0203 & #ff00) >> 8
	ld (var), a
___expanded_macro___1._sjasm_reusable_1_1:
	ld a, #0203 & #00ff
	ld (var), a
	or a
    jr nz, ___expanded_macro___1._sjasm_reusable_1_2
	ld a, (#0203 & #ff00) >> 8
	ld (var), a
___expanded_macro___1._sjasm_reusable_1_2:
loop:
	jr loop
var:
	db 0
__sjasm_page_0_end:
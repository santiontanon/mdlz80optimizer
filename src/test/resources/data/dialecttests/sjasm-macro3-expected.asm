; Test case: 
    ld a, (#0102 & #ff00) >> 8
    ld (var), a
    or a
    jr nz, ___expanded_macro___1._sjasm_reusable_1_1
    ld a, #0102 & #00ff
    ld (var), a
___expanded_macro___1._sjasm_reusable_1_1:
    ld a, (#0304 & #ff00) >> 8
    ld (var), a
    or a
    jr nz, ___expanded_macro___1._sjasm_reusable_1_2
    ld a, #0304 & #00ff
    ld (var), a
___expanded_macro___1._sjasm_reusable_1_2:
    ld a, (#0506 & #ff00) >> 8
    ld (var), a
    or a
    jr nz, ___expanded_macro___1._sjasm_reusable_1_3
    ld a, #0506 & #00ff
    ld (var), a
___expanded_macro___1._sjasm_reusable_1_3:
loop:
	jr loop
var:
	db 0
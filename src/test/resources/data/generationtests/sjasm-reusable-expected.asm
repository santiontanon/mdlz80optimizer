__sjasm_page_0_start:
	; Test to make sure reusable labels are parsed and generated correctly:
    add a, 10
    jr c, _sjasm_reusable_1_1
    ld a, (hl)
    jr _sjasm_reusable_2_1
_sjasm_reusable_1_1:    ld a, (bc)
_sjasm_reusable_2_1:    and #f0
loop:
    jr loop
__sjasm_page_0_end:
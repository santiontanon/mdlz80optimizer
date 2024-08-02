__sjasm_page_0_start:
; Test for the : operator
    ld bc, ((23) << 8) + (#63)
loop:
    jr loop
__sjasm_page_0_end:
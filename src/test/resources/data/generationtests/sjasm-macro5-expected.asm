__sjasm_page_0_start:
; Test case: 
    rrca
    rrca
    ld (hl), a
    rrca
    rrca
    rrca
    ld (bc), a
loop:
    jr loop
__sjasm_page_0_end:

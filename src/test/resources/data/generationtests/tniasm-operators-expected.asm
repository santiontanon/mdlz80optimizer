; Test case: 
    ld a, 0x40 + 0x60  ; ld a, $a0             ; (added)
    ld a, 0x40 | 0x60  ; ld a, $60             ; (ored *)
    ; ld a, ($40) or $60         ; ld a, ($0040): or $60 ; (two instructions *)
    ld a, 0x40
    or 0x60  ; ld a, $40: or $60     ; (two instructions)
    ld a, 0x40 + 0x60 | 0x40  ; ld a, $e0             ; (added, then ored)
    ; ld a, ($40 + $60) or $40   ; ld a, ($00a0): or $40 ; (two instructions *)
    ld a, 0x40 + (0x60 | 0x40)  ; ld a, $a0             ; (ored, then added)
    ld a, (0x40 + 0x60 | 0x40)  ; ld a, ($00e0)         ; (added, then ored; indirection)
    ld a, +(0x40 + 0x60 | 0x40)  ; ld a, $e0             ; (added, then ored)

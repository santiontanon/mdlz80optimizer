    call #0138 ;RSLREG
    rrca
    rrca
    and #03
    
    ; Secondary Slot
    ld c, a
    ld hl, #FCC1
    add a, l
    ld l, a
    ld a, [hl]
    and #80
    or c
    ld c, a
    inc l
    inc l
    inc l
    inc l
    ld a, [hl]
    
    ; Define slot ID
    and #0c
    or c
    ld h, #80
    
    ; Enable
    call #0024 ;ENASLT

    org #4000


main:
    ld a, 2
    ld de, 100
    call a_times_de_signed_fast2
    ld (res1), a  ; RET0-DESTINATION RET1-DESTINATION RET2-DESTINATION RET3-DESTINATION
    ld (rest2), hl
loop:
    jp loop


a_times_de_signed_fast2:
    ld l, a
    ld h, a_times_de_signed_fast_table_2 / 1024
    add hl, hl  ; 12
    add hl, hl  ; 12
    jp (hl)


a_times_de_signed_fast_table_2:
    jp a_times_de_signed_fast_000
    db 0
    jp a_times_de_signed_fast_001
    db 0
    jp a_times_de_signed_fast_002
    db 0
    jp a_times_de_signed_fast_003
    db 0


a_times_de_signed_fast_000:
    xor a
    ld h, a
    ld l, a
    ret  ; RET0 1


a_times_de_signed_fast_001:
    xor a
    ld h, a
    ld l, a
    ret  ; RET1 1


a_times_de_signed_fast_002:
    xor a
    ld h, a
    ld l, a
    ret  ; RET2 1


a_times_de_signed_fast_003:
    xor a
    ld h, a
    ld l, a
    ret  ; RET3 1


    org #4000


main:
    ld a, 2
    ld de, 100
    call a_times_de_signed_fast1
    ld (res1), a  ; RET0-DESTINATION RET1-DESTINATION RET2-DESTINATION RET3-DESTINATION
    ld (rest2), hl
loop:
    jp loop


a_times_de_signed_fast1:
    ld l, a
    ld h, a_times_de_signed_fast_table_1 / 512
    add hl, hl
    ld c, (hl)
    inc hl
    ld h, (hl)
    ld l, c
    jp (hl)


a_times_de_signed_fast_table_1:
    dw a_times_de_signed_fast_000, a_times_de_signed_fast_001, a_times_de_signed_fast_002, a_times_de_signed_fast_003


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


    org #4000

f1:
    ld a, 5
    ld b, a
    ld (v1), bc
    ret

f2:
    ld a, 5  ; mdl:self-modifying
    ld b, 5
    ld (v1), bc
    ret

    org #c000

v1:
    org $ + 2
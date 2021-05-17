    ld a, l
    rrca
    rrca
    rrca
    and 224
    ld h, a
    ld l, 0
----
    ld a, l
    ld l, 0
    rrca
    rrca
    rrca
    and 224
    ld h, a
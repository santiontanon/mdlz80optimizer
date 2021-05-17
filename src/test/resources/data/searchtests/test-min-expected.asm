    sub h
    ld b, a
    sbc a, a
    and b
    add a, h
----
    sub h
    ld b, a
    sbc a, b
    and b
    add a, h
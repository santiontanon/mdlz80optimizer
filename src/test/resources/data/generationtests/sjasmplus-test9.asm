; Test for some corner cases
    db v1
v1 = 1
    db v1
v1 = v1 + 1
    db v1
    db %11'01'11'00
    db 1'234
    db #01'02

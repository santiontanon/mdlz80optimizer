; FPOS tests
    org #4000
    db 1, 2, 3, 4
    fpos -2
    db 5, 6, 7, 8
    fpos +2
    db 3
    fpos 0
    db 0

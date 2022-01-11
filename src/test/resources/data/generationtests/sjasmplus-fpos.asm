; FPOS tests
    output "sjasmplus-fpos-expected.bin"

    device ZXSPECTRUM48

    org #4000

    db 1,2,3,4
    fpos -2
    db 5,6,7,8

    FPOS +2
    db {b #4002}

    FPOS 0
    db 0

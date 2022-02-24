; Test: some SDCC case

    ld hl, (var1)
    sra h
    rr l
    sra h
    rr l
    sra h
    rr l
    ld (var2), hl
loop:
    jr loop

var1:    dw 0
var2:    dw 0
    org #4000

main:
    ld de, label1
    push de
    jp function1
    nop  ; unreachable (but should not be a problem for the analyzer)
label1:
    nop  ; RET1-DESTINATION
loop:
    jp loop

function1:
    nop
    ret  ; RET1 1

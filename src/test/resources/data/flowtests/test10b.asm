    org #4000

main:
    call function1
loop:
    jp loop  ; RET1-DESTINATION


function1:
    ld de, label1
    push de
    jp function2
label1:
    nop  ; RET2-DESTINATION
    ret  ; RET1 1

    db 0  ; just some random statement here to see if it messes up the code

function2:
    nop
    ret  ; RET2 1

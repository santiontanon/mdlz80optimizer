.Z80

main:
    call function1
    call function2  ; RET1-DESTINATION
loop:
    jp loop  ; RET2-DESTINATION


function1::
    ld a, 1
    ret  ; RET1 1

function2:
    ld a, 2
    ret  ; RET2 1
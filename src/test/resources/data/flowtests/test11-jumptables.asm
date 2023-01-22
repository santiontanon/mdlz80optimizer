    org #4000

main:
    xor a
    call function1
loop:
    jp loop  ; RET0-DESTINATION


function1:
    ld hl, jump_table1
    ld e, a
    ld d, 0
    add hl, de
    ld de, function1_label
    push de
    jp hl  ; this is equivalent to a "call hl" (since the "push de" pushes the return address)
function1_label:
    ld a, 1  ; RET1-DESTINATION RET2-DESTINATION RET3-DESTINATION
    ret  ; RET0 1


jump_table1:
    jp jtf1
    nop
    jp jtf2
    nop
    jp jtf3


jtf1:
    ret  ; RET1 1

jtf2:
    ret  ; RET2 1

jtf3:
    ret  ; RET3 1

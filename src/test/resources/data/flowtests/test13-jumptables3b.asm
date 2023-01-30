    org #4000


main:
    xor a
    call function1
    nop  ; RET1-DESTINATION RET2-DESTINATION RET3-DESTINATION
    call function2
loop:
    ; Note: RET2 should never get here, but the current version of MDL cannot
    ;       know that. So, just for safety, it assumes it can.
    jp loop  ; RET1-DESTINATION RET2-DESTINATION RET3-DESTINATION


function1:
    call jump_table_jump
    dw jtf1
    dw jtf2
    dw jtf3

function2:
    call jump_table_jump
    dw jtf1
    dw jtf3


jump_table_jump:
    pop hl  ; Pointer to list
    call ADD_HL_2A
    ld e, (hl)  ; RET4-DESTINATION RET5-DESTINATION
    inc hl
    ld d, (hl)  ; DE = Address to jump
    ex de, hl
    jp (hl)


jtf1:
    ret  ; RET1 2

jtf2:
    ret  ; RET2 2

jtf3:
    ret  ; RET3 2


ADD_HL_2A:
    add a, a
ADD_HL_A:
    add a, l
    ld l, a
    ret nc  ; RET5 1
    inc h
    ret  ; RET4 1

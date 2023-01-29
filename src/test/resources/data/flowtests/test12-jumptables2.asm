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
    jp jtf1
    jp jtf2
    jp jtf3

function2:
    call jump_table_jump
    jp jtf1
    jp jtf3


jump_table_jump:
    ld l, a
    add a, a
    add a, l  ; a = a*3
    pop hl  ; get the pointer to the jump table
    ; hl += a:
    add a, l
    ld l, a
    jr nc, jump_table_jump_continue
    inc h
jump_table_jump_continue:
    jp hl



jtf1:
    ret  ; RET1 2

jtf2:
    ret  ; RET2 2

jtf3:
    ret  ; RET3 2


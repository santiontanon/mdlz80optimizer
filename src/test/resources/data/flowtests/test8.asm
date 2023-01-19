    org #4000

main:
    call function1
    call function2  ; RET1-DESTINATION
    call function3
    nop
loop:
    jp loop

function1:
    ld a, 1
    push af
    ld a, 2
    pop af
    ld a, 3
    ret  ; RET1 1

function2:
    pop af
    ret  ; RET2 0
	
function3:
    ld hl, (#1000)
    push hl
    ret  ; RET3 0

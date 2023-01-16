    org #4000

main:
    call function1
    call function2a  ; RET1-DESTINATION
    call function2b
    call function3
    call function4
    call function5
loop:
    jp loop


function1:
    ld d, 1
    ret  ; RET1 1
	
function2a:
    dec sp
    ld d, 2
    ret  ; RET2A 0

function2b:
    inc sp
    ld d, 2
    ret  ; RET2B 0

function3:
    ex (sp), hl
    ld d, 2
    ret  ; RET3 0

function4:
    ld sp, hl
    ret  ; RET4 0

function5:
    ld sp, #c000
    ret  ; RET5 0

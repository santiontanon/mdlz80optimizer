    org #4000

main:
    call function1
    call function2  ; RET1-DESTINATION
loop:
    jp loop  ; RET2-DESTINATION

function1:
    call function2
    ld e, 1  ; RET2-DESTINATION
    ret  ; RET1 1
	

function2:
    ld d, 2
    ret  ; RET2 2


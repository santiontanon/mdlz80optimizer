    org #4000

	call function1
    jp loop    ; RET1-DESTINATION RET2-DESTINATION

loop:
	jp loop


function1:
    ld a, (value)
    or a
    jr z, function1_label
    ret  ; RET1 1
function1_label:
    ld c, 1
    jp function2
	

function2:
    ld d, 2
    ret  ; RET2 1


value:
	db 1
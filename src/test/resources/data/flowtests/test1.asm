
    org #4000

    ld a, (value)
    or a
	call z,function1
    call function2  ; RET1-DESTINATION
end:
	jp end    ; RET1-DESTINATION


function1:
	ld b, 1
function1_label:
    ld c, 1
	ret  ; RET1 2


function2:
    ld b, 2
    jp function1_label


value:
	db 1
; test to verify the creation of safety labels
    org #4000
    call ___MDL_SAFETY_LABEL_1
    jp ___MDL_SAFETY_LABEL_2
    nop
loop:
___MDL_SAFETY_LABEL_2:    jp loop
___MDL_SAFETY_LABEL_1:    ret
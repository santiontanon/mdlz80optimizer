; test to verify the creation of safety labels
    org #4000
    call #400a
    jp #4007
    nop
loop:
    jp loop
    ret
; Test: no tail recursion optimization if the function assumes fixed stack offsets.

call_f1:
    ld a, 1
    push af
    inc sp  ; a is passed via de stack
    call f1  ; this should not be optimized, since f1 gets arguments in the stack
    ret

call_f2:
    ld a, 1
    call f2  ; this should be optimized to "jr f2"
    ret

f1:
    ld ix,0  ; this is how SDCC does it
    add ix,sp
    ld e,(ix-3)
    pop bc  ; recover the return address
    inc sp  ; remove the stack argument
    push bc
    ret


f2:
    ld e,a
    ret

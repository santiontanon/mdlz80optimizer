; Test case for the repetition macro in sjasmplus (and also, adding a BASIC-style EOF character at the end of this file)
N: equ 1
    call f1  ; this should be repeated 4 times
    call f1  ; this should be repeated 4 times
    call f1  ; this should be repeated 4 times
    call f1  ; this should be repeated 4 times
    nop  ; this should be repeated twice
    nop  ; this should be repeated twice
loop:
	jr loop
f1:
	ret

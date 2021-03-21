; Test case for the repetition macro in sjasmplus (and also, adding a BASIC-style EOF character at the end of this file)
   output "sjasmplus-test7-expected.bin"

N: equ 1

.4 call f1 ; this should be repeated 4 times

.(N+1) nop ; this should be repeated twice

loop:
	jr loop

f1:
	ret

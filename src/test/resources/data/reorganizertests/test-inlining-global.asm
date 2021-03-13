; Test of the function inlining feature (in SDCC, with global labels)

	.globl func1

	.org 0x4000

	; calling func1 (should not be inlined since it's declared as global)
	call func1
	push af

	; calling func2 (should not be inlined since it's declared as global)
	call func2
	push af

loop:
	jr loop


func1:
	ld a,(bc)
	ret

func2::
	ld a,(de)
	ret

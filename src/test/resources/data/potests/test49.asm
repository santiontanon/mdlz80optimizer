; test to check operators with unused results

	org #4000

    xor a
    inc a	; these two should be replaced by ld a,1
    and a	; this instruction should be removed
    or a
    out (98h),a
    jr c,loop
    nop
loop:
	jr loop
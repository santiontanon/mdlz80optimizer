; Test to check the behavior of phase/dephase
	org #4000
	.rom
    .start loop

loop:
	jr loop

	.phase #c000
method1:
	call method2
	ret

method2:
	ret
	.dephase

method3:
	call method4
	ret

method4:
	ret

; Test to check the behavior of phase/dephase
	org #4000
    db "AB", loop % 256, loop / 256, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
loop:
	jr loop
__asmsx_phase_pre_1:    org #c000
__asmsx_phase_post_1:
method1:
	call method2
	ret
method2:
	ret
__asmsx_dephase_1:    org __asmsx_phase_pre_1 + (__asmsx_dephase_1 - __asmsx_phase_post_1)
method3:
	call method4
	ret
method4:
	ret
	ds #6000 - $, 0

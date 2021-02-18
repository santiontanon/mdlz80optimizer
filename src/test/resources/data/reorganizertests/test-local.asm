; asMSX: Making sure reorganizing blocks of code that involve local labels are handled fine

	org #4000
global1:
	ld hl,variable
	jp global2.local
.local:
	inc hl
	ld [hl],2
loop:
	jp loop

global2:
	ret
.local:
	ld [hl],1
	jp global1.local

	org #c000
variable: ds 1
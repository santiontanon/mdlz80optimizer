; asMSX: Making sure reorganizing blocks of code that involve local labels are handled fine
	org #4000
global1:
	ld hl, variable
; 	jp global2.local  ; -mdl
global2_local:
	ld [hl], 1
; 	jp global1.local  ; -mdl
global1_local:
	inc hl
	ld [hl], 2
loop:
	jp loop
global3:
global2:
	ret
global3_local:
	ld [hl], 2
	ret
	org #c000
variable:
	org $ + 1
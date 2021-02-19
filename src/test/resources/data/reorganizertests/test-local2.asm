; Glass: Making sure reorganizing blocks of code that involve local labels are handled fine

	org #4000
global1: proc
	ld hl,variable
	jp global2.local
local:
	inc hl
	ld [hl],2
endp

loop:
	jp loop

global2: proc
	ret
local:
	ld [hl],1
	jp global1.local
endp

global3: proc
	jp z,local
	ret
local:
	ld [hl],2
	ret
endp

	org #c000
variable: ds virtual 1
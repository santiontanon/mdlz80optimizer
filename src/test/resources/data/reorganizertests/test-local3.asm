	.module infobar
	.optsdcc -mz80
	.area _CODE
_infobar_update_map::
00173$:	
	ret
_infobar_update_rupees::
	push	ix
00102$:
	xor a
	jp	Z,00173$
	jr	00104$
	jp	00173$
00104$:
	xor a
00173$:
	pop	ix
	ret
00176$
	ret
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)

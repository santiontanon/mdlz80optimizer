	.module infobar
	.optsdcc -mz80
	.area _CODE
_infobar_update_map::
00173$:
    ret
    jp _infobar_update_rupees00173
_infobar_update_rupees::
    push ix
00102$:
    xor a
    jp z, _infobar_update_rupees00173
  ; 	jr	00104$  ; -mdl
00104$:
    xor a
_infobar_update_rupees00173:
    pop ix
    ret
_infobar_update_rupees00176:
	ret
	.area _CODE
	.area _INITIALIZER
	.area _CABS (ABS)
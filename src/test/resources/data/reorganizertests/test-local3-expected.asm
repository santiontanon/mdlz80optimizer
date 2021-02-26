s__CODE:
_infobar_update_map:
_infobar_update_map00173:
    ret
    jp _infobar_update_rupees00173
_infobar_update_rupees:
    push ix
_infobar_update_rupees00102:
    xor a
    jp z, _infobar_update_rupees00173
; 	jr	00104$  ; -mdl
_infobar_update_rupees00104:
    xor a
_infobar_update_rupees00173:
    pop ix
    ret
_infobar_update_rupees00176:
	ret
s__INITIALIZER:
s__CABS:
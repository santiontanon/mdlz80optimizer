; I have seen SDCC generate code like this. The "bd #20" is a "ex (sp),hl". Not sure why is it
; generated this way. But just to be safe, MDL should stop the dependency chains there and
; not remove the "ld a, 1" just to be safe:

	org #4000
 	jr  nz, label
  	ld  a, 1
 	db #20
label:
  	xor a, a
  	ld (hl), a

loop:
	jr loop

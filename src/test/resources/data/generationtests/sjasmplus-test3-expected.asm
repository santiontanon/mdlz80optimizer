; Test case encountered when parsing aplib
Decompress:    ld a, 128
CASE0:    ldi
MainLoop:    
   add a, a
   call z, ReloadByte
   jr nc, CASE0
   add a, a
   call z, ReloadByte
   jr nc, CASE10
   ld bc, 224
   add a, a
   call z, ReloadByte
   jr nc, CASE110
CASE110:
CASE10:
NoLWM:
   bit 7, l
   jr nz, NoLWM.Add0
NoLWM.Add2:    inc bc
NoLWM.Add1:    inc bc
NoLWM.Add0:    ex af, af'
loop:
	jr loop
ReloadByte:    ld a, (hl)
   inc hl
   rla
   ret
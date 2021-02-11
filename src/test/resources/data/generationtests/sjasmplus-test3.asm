; Test case encountered when parsing aplib
   DEFINE FasterGetBit


   IFDEF FasterGetBit
      MACRO   GET_BIT
         add a : call z,ReloadByte
      ENDM
   ELSE
      MACRO   GET_BIT
         call GetOneBit
      ENDM
   ENDIF


@Decompress:	ld a,128
CASE0:         	ldi
MainLoop:      	GET_BIT : jr nc,CASE0
         GET_BIT : jr nc,CASE10
         ld bc,%11100000
         GET_BIT : jr nc,CASE110
CASE110:
CASE10:
NoLWM
     	 bit 7,l : jr nz,.Add0
.Add2         inc bc
.Add1         inc bc
.Add0         exa
loop:
	jr loop

   IFNDEF FasterGetBit
GetOneBit:      add a : ret nz
   ENDIF
ReloadByte:      ld a,(hl) : inc hl
         rla : ret
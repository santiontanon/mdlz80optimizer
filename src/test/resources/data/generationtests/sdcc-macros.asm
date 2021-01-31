; Test case: Thanks to Bengalack for this test case to test asdz80 macros

.macro vdpOut, registercommand, data
  ld  a, data
  out ( #0x99 ), a
  ld  a, registercommand
  out ( #0x99 ), a
.endm


.macro vdpReadyNI ?noparameter_VDPready

  ld  a,#2
  out (#0x99),a          ;select status register 2
  ld  a,#128+#15
  out (#0x99),a
noparameter_VDPready:
  in  a,(#0x99)

  and #1
  jp  nz, noparameter_VDPready  ; wait TODO: double check that we really can pump values from in. and not set reg for every read.

  xor a           ; always set S=0 when leaving
  out (#0x99),a
  ld  a,#128+#15 ; 0x8F
  out (#0x99),a
.endm

.rept 2
	halt
.endm
	vdpOut #0x00, #0x01
	vdpReadyNI
	vdpReadyNI




; Test case: 
macro m1 2
  ld  @2,a  ; 5
  rla   ; 5
  sbc a ; 5
  ld  @1,a  ; 5
endmacro

macro: m2 2
  ld  @2,a  ; 5
  rla   ; 5
  sbc a ; 5
  ld  @1,a  ; 5
endmacro

	m1 b,c
	m2 h,l

loop:
	jr loop

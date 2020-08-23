; Test case: 
  ld c, a  ; 5
  rla  ; 5
  sbc a, a  ; 5
  ld b, a  ; 5
  ld l, a  ; 5
  rla  ; 5
  sbc a, a  ; 5
  ld h, a  ; 5
loop:
	jr loop
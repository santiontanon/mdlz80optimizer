; Comments start with some random character ,
; and only end when that character is found again
    org 4000H
START:
	jr START
LF1:
	ld a, (hl)
	ret
LF2:
	ld a, (de)
	ret
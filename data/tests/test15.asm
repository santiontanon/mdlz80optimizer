; Test case: 
; - lines 4-5 should be optimized
; - lines 9-10 should be optimized to djnz

	ld b,1
	ld c,2
	ld (var1),bc
loop1:
	dec b
	jr nz,loop1
loop2:
	jp loop2

var1:
	dw 0

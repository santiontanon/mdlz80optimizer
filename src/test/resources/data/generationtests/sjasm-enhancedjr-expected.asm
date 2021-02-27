__sjasm_page_0_start:
; Test case: 
label1:
	jr label1
	jr label1
	djnz label1
	jp label2
	jp label2
	dec b
	jp nz, label2
loop:
	jp loop
buffer:
	ds 1024, #ff
label2:
__sjasm_page_0_end:
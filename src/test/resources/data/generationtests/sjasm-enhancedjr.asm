; Test case: 

label1:
	jp. label1
	jr. label1
	djnz. label1

	jp. label2
	jr. label2
	djnz. label2

loop:
	jp loop

buffer:
	ds 1024, #ff

label2:

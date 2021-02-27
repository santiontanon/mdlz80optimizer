__sjasm_page_0_start:
__sjasm_page_0_end:
	org #4040
__sjasm_page_0_output1_start:
data1:
    db #ff
    db 0
    db 0
    db 1
start:
    jp start
__sjasm_page_0_output1_end:
    org #8080
__sjasm_page_1_output1_start:
data2:
	dw $
    dw (__sjasm_page_0_output1_end - __sjasm_page_0_output1_start)
    dw (__sjasm_page_1_output1_end - __sjasm_page_1_output1_start)
__sjasm_page_1_output1_end:

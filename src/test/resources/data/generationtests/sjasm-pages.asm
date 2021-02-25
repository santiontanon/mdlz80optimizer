    output sjasm-pages-expected.bin

    defpage 0, $4040
    defpage 1, $8080

    code 
start:
    jp start

    code
data1:
    db #ff
    db :data1
    db :start
    db :data2

    page 1
data2:
	dw $
    dw ::0
    dw ::1

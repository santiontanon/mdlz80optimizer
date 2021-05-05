; Test case: 

jp begin exit: ld b,0042h call f1 begin: ld de,text

loop:
    jr loop

f1:
    outi outi outi outi
    or a
    ret z
    outi outi
    ret

text: db "hello"
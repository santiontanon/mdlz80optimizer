; Test for array definitions
    DEFARRAY myarray 10*20,"A",20
CNT DEFL 0
    DUP myarray[#]
    db myarray[CNT]
CNT DEFL CNT+1
    EDUP
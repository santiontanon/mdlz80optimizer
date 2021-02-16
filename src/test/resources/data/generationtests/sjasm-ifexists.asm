; Test case: 
ifexists sjasm-ifexists.asm
label1:
endif
ifexists sjasm-ifexists2.asm
label2:
endif
ifnexists sjasm-ifexists.asm
label3:
endif
ifnexists sjasm-ifexists2.asm
label4:
endif

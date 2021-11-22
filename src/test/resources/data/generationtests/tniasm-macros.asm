; Test case: 

GAME_VERSION: equ "2"

    %if	(GAME_VERSION == "2")
    ADD	A,0Ah
    %endif

    %if	(GAME_VERSION == "2") \ ADD A,0Bh \ %endif

    %if	(GAME_VERSION == "4")
    SUB	08h
    %else
    ADD A,0Ch
    %endif

    %macro	ips_cls		%n , %n , %n	; start address, size, fill byte
     %if (#1 > FFFFFFh) | (#2 > FFFFh) | (#3 > FFh)
      %if (#1 > FFFFFFh) \ %error "ips_cls offset address is too big!" \ %endif \ %if (#2 > FFFFh) \ %error "ips_cls size is too big!" \ %endif \ %if (#3 > FFh) \ %error "ips_cls fill byte is too big!" \ %endif
     %else
      DB	(#1 / 10000h)
      DB	(#1 / 100h) & FFh
      DB	(#1 % 100h)
      DW	0000h
      DB	(#2 / 100h)
      DB	(#2 % 100h)
      DB	#3
     %endif
    %endmacro

    ips_cls #0001, #0002, #0003
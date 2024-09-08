%macro mymacro2 %n
    %if (#1 > 10)
        db 10
    %else
        db 20
    %endif
%endmacro


%macro mymacro %n
    mymacro2 .#len
.#start:
    db #1, #1
.#len: equ $ - .#start
%endmacro


    org #4000
label:
    mymacro 1
    mymacro 1

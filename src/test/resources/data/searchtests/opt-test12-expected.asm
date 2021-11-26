    org #4000

execute:
    ld (v1), hl

loop:
    jr loop

    org #c000
v1:
    org $ + 2

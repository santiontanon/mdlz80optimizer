; Test case:

start:
    ld a, 1
    cp 2
    push af
        inc b
        ld (#ffff), bc
    pop af
    jr z, loop
    push bc
loop:
    jr loop

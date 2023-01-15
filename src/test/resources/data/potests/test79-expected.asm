
function1:
    ld a, #20
    ld (ix + #00), a
    ld (ix + #01), a
    ld (ix + #18), a
    ld (ix + #19), a
    ld bc, 0 - #18 * #18
    add ix, bc
    ld (ix + #00), a
    ld (ix + #01), a
    ld (ix + #18), a
    ld (ix + #19), a

loop1:
    jr loop1

function2:
    ld h, #20
    ld (ix + #00), h
    ld (ix + #01), h
    ld (ix + #18), h
    ld (ix + #19), h
    ld b, 1
    ld c, a
    add ix, bc
    ld (ix + #00), h
    ld (ix + #01), h
    ld (ix + #18), h
    ld (ix + #19), h
loop2:
    jr loop2


function3:
    ld b, 1
    ld c, a
    add ix, bc
    ld (ix + #00), b
loop3:
    jr loop3

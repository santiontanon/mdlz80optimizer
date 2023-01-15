
function1:
    ld (ix + #00), #20
    ld (ix + #01), #20
    ld (ix + #18), #20
    ld (ix + #19), #20
    ld bc, 0 - #18 * #18
    add ix, bc
    ld (ix + #00), #20
    ld (ix + #01), #20
    ld (ix + #18), #20
    ld (ix + #19), #20
loop1:
    jr loop1



function2:
    ld (ix + #00), #20
    ld (ix + #01), #20
    ld (ix + #18), #20
    ld (ix + #19), #20
    ld b, 1
    ld c, a
    add ix, bc
    ld (ix + #00), #20
    ld (ix + #01), #20
    ld (ix + #18), #20
    ld (ix + #19), #20
loop2:
    jr loop2


function3:
    ld b, 1
    ld c, a
    add ix, bc
    ld (ix + #00), 1
loop3:
    jr loop3

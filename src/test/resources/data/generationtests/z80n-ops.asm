; Test case: 
    org #0000

    nextreg 1, 2
    nextreg 1, A
    add bc, #0102
    add de, #0102
    add hl, #0102
    add bc, a
    add de, a
    add hl, a
    swapnib
    mul d, e
    ldws
    ldix
    ldirx
    lddx
    lddrx
    ldpirx
    outinb
    mirror a
    push #0102
    pixeldn
    pixelad
    setae
    test #01
    bsla de, b
    bsra de, b
    bsrl de, b
    bsrf de, b
    brlc de, b
    jp (c)
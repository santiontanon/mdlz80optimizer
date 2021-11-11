; Test case for Z180-specific instructions: 
    org #0000

    in0  a, (#01)
    mlt bc
    out0 (#01), b
    otim
    otdm
    otimr
    otdmr
    slp
    tst c
    tst #02
    tstio #02

BIOS_CLS: equ #0d6b
BIOS_LOAD: equ #0556

    org #5E00

    call BIOS_CLS
    ld hl,EXECUTION_START
    push hl
    ld ix,BINARY_START
    ld de,BINARY_LENGTH
    ld a, 255
    scf  
    jp BIOS_LOAD

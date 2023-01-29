; Loader with a loading screen

BIOS_CLS: equ #0d6b
BIOS_LOAD: equ #0556

    org #5E00

    call BIOS_CLS

    ld ix, #4000
    ld de, 6912
    ld a, 255
    scf  
    call BIOS_LOAD

    ld hl, EXECUTION_START
    push hl
    ld ix, BINARY_START
    ld de, BINARY_LENGTH
    ld a, 255
    scf  
    jp BIOS_LOAD

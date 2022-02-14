; Test for struct definition and usage
SCOLOR: equ 3
SCOLOR.RED: equ 0
SCOLOR.GREEN: equ 1
SCOLOR.BLUE: equ 2
SDOT: equ 5
SDOT.X: equ 0
SDOT.Y: equ 1
SDOT.C: equ 2  ; use new default values
SDOT.C.RED: equ 2
SDOT.C.GREEN: equ 3
SDOT.C.BLUE: equ 4
COLORTABLE:
    db 0
    db 0
    db 0
    db 1
    db 2
    db 3
    db 4
    db 2
    db 6
DOT1:
DOT1.X:    db 1
DOT1.Y:    db 2
DOT1.C.RED:    db 3
DOT1.C.GREEN:    db 4
DOT1.C.BLUE:    db 5
DOT2:
DOT2.X:    db 1
DOT2.Y:    db 2
DOT2.C.RED:    db 3
DOT2.C.GREEN:    db 4
DOT2.C.BLUE:    db 5
DOT3:
DOT3.X:    db 1
DOT3.Y:    db 2
DOT3.C.RED:    db 3
DOT3.C.GREEN:    db 4
DOT3.C.BLUE:    db 5
DOT4: equ #c000
DOT4.X: equ #c000 + 0
DOT4.Y: equ #c000 + 1
DOT4.C.RED: equ #c000 + 2
DOT4.C.GREEN: equ #c000 + 3
DOT4.C.BLUE: equ #c000 + 4
; Making sure jump tables are detected, and not re-organized
	org #4000
start:
    ld de, variable
    ld a, (de)
    ld b, a
    ld c, b
    ld a, b
    inc a
    ld (de), a
    ld a, #05
    sub b
    jp c, skip
    ld b, #00
    ld hl, label1
    add hl, bc
    add hl, bc
    add hl, bc
    jp (hl)
label1:  ; mdl:no-opt (mdl suspects this is a jump table)
    jp jumptarget1  ; mdl:no-opt (mdl suspects this is a jump table)
    jp jumptarget2  ; mdl:no-opt (mdl suspects this is a jump table)
    jp jumptarget3  ; mdl:no-opt (mdl suspects this is a jump table)
    jp jumptarget4  ; mdl:no-opt (mdl suspects this is a jump table)
    jp jumptarget5  ; mdl:no-opt (mdl suspects this is a jump table)
    jp jumptarget6  ; mdl:no-opt (mdl suspects this is a jump table)
jumptarget3:
jumptarget4:
jumptarget5:
jumptarget6:
jumptarget2:
	nop
	jp skip
jumptarget1:
	nop
; 	jp skip  ; -mdl
skip:
loop:
	jr loop
	org #c000
variable:
    org $ + 2
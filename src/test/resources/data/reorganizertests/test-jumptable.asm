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
label1:
    jp jumptarget1
    jp jumptarget2
    jp jumptarget3
    jp jumptarget4
    jp jumptarget5
    jp jumptarget6
jumptarget1:
	nop
	jp skip
jumptarget2:
	nop
	jp skip
jumptarget3:
	nop
	jp skip
jumptarget4:
	nop
	jp skip
jumptarget5:
	nop
	jp skip
jumptarget6:
	nop
	jp skip

skip:

loop:
	jr loop

	org #c000
variable: ds virtual 2
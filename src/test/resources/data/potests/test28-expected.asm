; Test case:
ONE: equ 1

	ld hl, list + ONE
	ld a, (hl)
	ld (var), a

    ld l, (list2 + 0) & #00ff
	ld a, (hl)
	ld (var), a

	ld hl, #ffff
	ld ix, list
	ld a, (ix)  ; this should not be optimized, as we would modify hl
	ld (var), a
	ld a, (hl)
	ld (var), a

__mdlrenamed__end:
	jr __mdlrenamed__end

var:
	db 0

list:
	db 1, 2, 3, 4
list2:
	db 5, 6, 7, 8
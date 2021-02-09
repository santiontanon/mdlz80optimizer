; Making sure the edge case of instructions directly specified as data is handled
; in this case, just for safety, the blocks before and after the "db" statement
; should not be moved.
	ld a, 1
	jp label1
label2:  ; ideally, we would want this block to be moved at the very end, 
         ; but the "db 0" breaks the block in two, and for safety, no 
         ; optimization is made.
	ld (hl), a
label3:
	jr label3
label1:
	add a, b
	db 0  ; this is a "nop", but specified directly as a byte
	jp label2

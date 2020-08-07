; Test case:

	org #4000-4

	ld hl,label1
	ld a,1			; here, this looks like it should be optimized to ld a,l, 
					; but then that will change label1 to #3fff
					; So, this optimization is wrong, which should be detected and undone
					
label1:				; this label (without optimization) should be #4001
	ld (hl),a

loop:
	jr loop


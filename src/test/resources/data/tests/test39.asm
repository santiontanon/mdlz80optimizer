; Test case:

	org #4000-5

	ld hl,label1
	ld a,0			; here, this looks like it should be optimized to ld a,l, 
					; but then that will change label1 to #3fff
					; So, this optimization is wrong, which should be detected and undone
					
label1:				; this label (without optimization) should be #4000
	ld (hl),a

loop:
	jr loop


; Test to prevent a corner case reported by jltursan

	org #4000

	di
	ld sp,hl					; 7
	pop de						; 11 fetch sine
	pop bc						; 11 fetch cosine
	ld sp,ix
	pop hl
	pop hl	
	add hl,de
	push hl	; mdl:no-opt
	pop hl
	pop hl
	add hl,bc
	push hl				
	ei

loop:
	jr loop
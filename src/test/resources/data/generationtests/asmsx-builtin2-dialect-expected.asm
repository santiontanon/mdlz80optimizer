; Test case: 
	org #4000
	.megarom konami
    .start loop
    .subpage 1 at #6000
    .subpage 2 at #6000
loop:
	jp loop
	push af
	ld a, 1
	ld [#6000], a
	pop af
    ld a, 3
	ld [#7000], a
    ld b, 5
	push af
	ld a, b
	ld [#8000], a
	pop af	
.printtext      "hello!"

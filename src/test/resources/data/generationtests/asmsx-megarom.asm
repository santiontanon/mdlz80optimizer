; Test case: 
	.megarom ascii8
    .start loop

    .subpage 1 at #6000
    .subpage 2 at #6000

loop:
	jP loop

    .select 1 at 06000h

    ld a,3
    .select a at 07000h

    ld b,5
    .select b at 08000h

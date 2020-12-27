; Test case: 
	org #4000
	.megarom konami
    .start loop

loop:
	jr loop

    .select 1 at 06000h

    ld a,3
    .select a at 07000h

    ld b,5
    .select b at 08000h

.printtext      "hello!"

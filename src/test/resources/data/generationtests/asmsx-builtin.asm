; Test case: 
	org #8000
	.rom
    .start loop

loop:
	jp loop

.printtext "hello!"

; Test case: 
	org #4000
	.rom
    .start loop

loop:
	jp loop

.printtext "hello!"

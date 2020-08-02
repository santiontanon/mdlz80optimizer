; Test case: 
	org #4000
	.rom
    .start  loop

loop:
	jr loop

.printtext      "hello!"

; Test case:

l2b:
	ds 125
l3a:
	jp l2b	; <-- should be optimized to jr
        jp l2b
        jp l4a
	jp l4a	; <-- should be optimized to jr
l3b:
	ds 127
l4a:
	jp l3b
	jp l5a
l4b:
	ds 128
l5a:

loop:
	jp loop ; <-- should be optimized to jr


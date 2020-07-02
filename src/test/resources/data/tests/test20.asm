; Test case: 

	cp 1
	jp c,label1		; <-- these two jumps should be optimized
	jp z,label1

	cp 255			; <-- this should be changed to a inc a
	jp c,label1		; <-- this one should NOT be optimized
	jp z,label1

end:
    jp end

label1:
	jp end

var1:
    db 0
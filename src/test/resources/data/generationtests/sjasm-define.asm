; Test case: 

	define kip "hoppa!"
	byte kip,0,0,kip
	byte "kip"

	define one 1
	byte one
	define one 2
	byte one

  	define val 1
  	xdefine val val+1  ; c expands to 1+1
  	byte val

  	define val 1
  	assign val val+1   ; c expands to 2
  	byte val

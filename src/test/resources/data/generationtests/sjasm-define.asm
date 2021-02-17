; Test case: 
label1:
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

	define composite1 "abc",100,"ef"
	define composite2 "    ",#ff
	byte composite1
	byte composite2
label2:
	byte label2-label1
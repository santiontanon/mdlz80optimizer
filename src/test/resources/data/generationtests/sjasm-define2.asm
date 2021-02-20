; Test case: 
	define m1(param) byte param*param\ word 0
	m1(1)
	m1(2)
	define m2(param) byte param*param
  	define m3(param1, param2) byte param1*param1, param2+param2
  	m2(3)
  	m3(4,5)
    define MacroColorBordeDestruyeA(color)  ld A,color\ out [099h],A\ LD A,087h\  out [099h],A
    MacroColorBordeDestruyeA(15)
 	define one 1
  	xdefine two one+one
  	define one 3
  	byte two
  	define d 1
  	xdefine d d+1
  	byte d
  	define e 1
  	assign e e+1
  	byte e

  define three 3  ; definition with comment
  byte (three)*2

  DEFINE VT_CLSCREND    27,"J"  ; Clear to end of screen: Clear screen from cursor onwards.
  byte 1, VT_CLSCREND, 2
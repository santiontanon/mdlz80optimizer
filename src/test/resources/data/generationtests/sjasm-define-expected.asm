; Test case: 
label1:
	db "hoppa!", 0, 0, "hoppa!"
  	db "kip"
	db 1
  	db 2
	db 1 + 1
  	db 2
  	db "abc", 100, "ef"
  	db "    ", #ff
label2:
	db label2 - label1
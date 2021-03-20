; Test case for eager macro evaluation and unary operator precedence
   org #4000
label:
   db 1 * 8 + 2
   db 3 * 8 + 4
label2:
   dw label, label2, 16384, 16705

; Test case for eager macro evaluation and unary operator precedence
   output "sjasmplus-test6-expected.bin"

HASHBIT     macro b,o
   db b*8+o
   endm

   org #4000

label:
   HASHBIT 1,2   
   HASHBIT 3,4
eagervar = $ - 2
eagervar2 = 256 * (high $ + 1) + (high $ + 1)
label2:
   dw label, label2, eagervar, eagervar2

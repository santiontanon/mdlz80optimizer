org #0000

.define L 0

    ld a,L
    
.undefine L

    ld a,L

.dw +, ++, +++
+: db 1
++: db 2
+++: db 3

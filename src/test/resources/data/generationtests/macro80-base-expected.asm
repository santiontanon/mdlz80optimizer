; symbols in macro80 are case-insensitive (always translated to upper case):
LABEL1:
	jp LABEL1
; numeric constant notation of macro80:
db 00111111B, 127, 77O, 77O, 7FH, 7FH
db "string", "string"
db 1000 & 00FFH, (1000 & 0FF00H) >> 8, 1000 % 10, 1000 >> 1, 1000 << 1
db ~ (10 = 10), 10 = 20, 10 != 10, 10 < 20, 10 <= 10, 10 > 10, 10 >= 10
db (10 = 10) & (20 = 20)
db "AB" & 00FFH
db "strin", "g" | 80H
db 1
db 1
db 1
db 1
    nop
db 1
db 2
db 3
db 4
db 1
db 2
db 3
db 4
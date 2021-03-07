title A test case for soe basic macro-80 syntax
.Z80
; symbols in macro80 are case-insensitive (always translated to upper case):
label1:
	jp LABEL1
; numeric constant notation of macro80:
db 0111111B, 127D, 77O, 77Q, 7fH, X'7f' 
db 'string', "string"
db LOW 1000, HIGH 1000, 1000 MOD 10, 1000 SHR 1, 1000 SHL 1
db NOT (10 EQ 10), 10 EQ 20, 10 NE 10, 10 LT 20, 10 LE 10, 10 GT 10, 10 GE 10
db (10 EQ 10) AND (20 EQ 20)
db 'AB' AND 0FFH
dc 'string'

REPT 4
	defb 1
ENDM

COND 1 EQ 1
	nop
ENDC

IRP X,<1,2,3,4>
    db X
ENDM

FOO MACRO X
Y   SET 0
	REPT X
Y   SET Y+1
	DB Y
	ENDM
	ENDM

FOO 4
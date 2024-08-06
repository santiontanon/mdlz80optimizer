    org #0000
start:
    push af, bc, hl
    pop hl, bc, af

	nop	| nop	| nop
	outi	| outi	| outi	| outi	| outi	| outi	| outi	| outi
; Player effects handler routines
	ret	| db	$ff, $ff ; (padding)
	dw	start + 14		| db	"PUSH SPACE KEY", $00
end:
    jr end

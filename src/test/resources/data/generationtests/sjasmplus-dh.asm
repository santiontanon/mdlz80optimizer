; Test case for dh directive (hex string to data bytes)
	dh "48656C6C6F"
	dh "FF00AB"
; Unquoted hex
	dh 48656C6C6F
; Single-quoted hex
	dh '48656C6C6F'
; Spaces in quoted hex
	dh "01 23 45 67"
; Unquoted hex with spaces
	dh 01 23 45 67
; Comma-separated quoted
	dh "0123","4567"
; defh alias
	defh "FF00AB"
; hex alias
	hex "FF00AB"
; Unquoted comma-separated
	dh 01,23,45,67

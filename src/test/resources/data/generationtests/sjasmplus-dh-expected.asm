; Test case for dh directive (hex string to data bytes)
	db #48, #65, #6c, #6c, #6f
	db #ff, #00, #ab
; Unquoted hex
	db #48, #65, #6c, #6c, #6f
; Single-quoted hex
	db #48, #65, #6c, #6c, #6f
; Spaces in quoted hex
	db #01, #23, #45, #67
; Unquoted hex with spaces
	db #01, #23, #45, #67
; Comma-separated quoted
	db #01, #23, #45, #67
; defh alias
	db #ff, #00, #ab
; hex alias
	db #ff, #00, #ab
; Unquoted comma-separated
	db #01, #23, #45, #67

title Some more macro80 syntax
.Z80
.COMMENT $ Comments start with some random character,
		   and only end when that character is found again $

EXT ef1, ef2
ENTRY lf1, lf2

ORG 0x4000

start:
	jr start

*EJECT 50

lf1:
	ld a,(hl)
	ret

lf2:
	ld a,(de)
	ret

.PRINTX | completed parsing the file |

END 0x4000
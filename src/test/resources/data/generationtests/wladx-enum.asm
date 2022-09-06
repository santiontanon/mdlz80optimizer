.STRUCT mon
name ds 2
age  db
.ENDST

.ENUM $A000
_scroll_x DB
_scroll_y DB
player_x: DW
player_y: DW
map_01:   DS  16
map_02    DSB 16
map_03    DSW  8
monster   INSTANCEOF mon 3
dragon    INSTANCEOF mon
.ENDE

.STRUCT position_t
pos_x  DW
pos_y  DW
.ENDST

.STRUCT enemy_t
id     DW
       INSTANCEOF position_t ; here we import fields from position_t
pos2   INSTANCEOF position_t
health DW
.ENDST

.ENUM $A000
nemesis INSTANCEOF enemy_t
.ENDE

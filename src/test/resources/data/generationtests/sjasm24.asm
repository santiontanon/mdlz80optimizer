  module V9990

; Sprite attributes
struct SPRITE_ATTRIBUTE_DATA
Y        db  0                            ; Y position of sprite (actual display is one line below)
PATTERN  db  0                            ; Sprite pattern number
X        db  0                            ; X position of sprite
FLAGS    db  0                            ; SC5-4, P, D, X (bit9-8)
ends

  endmodule

  module Data

MAX_UNIT_SPRITES        equ 100
UNIT_SPRITES            #V9990.SPRITE_ATTRIBUTE_DATA * MAX_UNIT_SPRITES
TMP                     #1

  endmodule

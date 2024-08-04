__sjasm_page_0_start: equ $
; Sprite attributes
V9990.SPRITE_ATTRIBUTE_DATA: equ 4
V9990.SPRITE_ATTRIBUTE_DATA.Y: equ 0  ; Y position of sprite (actual display is one line below)
V9990.SPRITE_ATTRIBUTE_DATA.PATTERN: equ 1  ; Sprite pattern number
V9990.SPRITE_ATTRIBUTE_DATA.X: equ 2  ; X position of sprite
V9990.SPRITE_ATTRIBUTE_DATA.FLAGS: equ 3  ; SC5-4, P, D, X (bit9-8)
Data.MAX_UNIT_SPRITES: equ 100
Data.UNIT_SPRITES: equ #0000
Data.TMP: equ #0190
__sjasm_page_0_end:
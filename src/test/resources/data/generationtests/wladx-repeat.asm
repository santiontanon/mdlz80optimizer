.define tile $21
.macro Rating args width, height, px, c1, c2, c3, c4
  .db width*height
  .db (248-px*2)/2+8
  .repeat height index n
    .repeat width
      .db tile
      .redefine tile tile+1
    .endr
    .if n < (height-1)
      .db 0
    .endif
  .endr
  .db c1, c2, c3, c4
.endm

.org #8000

RatingPerfect:  Rating 11, 2, 87, 0, 1, 2, 3


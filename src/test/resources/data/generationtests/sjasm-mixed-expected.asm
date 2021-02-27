__sjasm_page_0_start: equ $
slide: equ 8
centrifuge: equ 8
speed_max: equ 16
go:    cp speed_max * 4
    ld a, speed_max * 4
  db (0 + 1) * slide / speed_max, -(0 + 1) * slide / speed_max
  db (1 + 1) * slide / speed_max, -(1 + 1) * slide / speed_max
  db (2 + 1) * slide / speed_max, -(2 + 1) * slide / speed_max
  db (3 + 1) * slide / speed_max, -(3 + 1) * slide / speed_max
  db (4 + 1) * slide / speed_max, -(4 + 1) * slide / speed_max
  db (5 + 1) * slide / speed_max, -(5 + 1) * slide / speed_max
  db (6 + 1) * slide / speed_max, -(6 + 1) * slide / speed_max
  db (7 + 1) * slide / speed_max, -(7 + 1) * slide / speed_max
  db (8 + 1) * slide / speed_max, -(8 + 1) * slide / speed_max
  db (9 + 1) * slide / speed_max, -(9 + 1) * slide / speed_max
  db (10 + 1) * slide / speed_max, -(10 + 1) * slide / speed_max
  db (11 + 1) * slide / speed_max, -(11 + 1) * slide / speed_max
  db (12 + 1) * slide / speed_max, -(12 + 1) * slide / speed_max
  db (13 + 1) * slide / speed_max, -(13 + 1) * slide / speed_max
  db (14 + 1) * slide / speed_max, -(14 + 1) * slide / speed_max
  db (15 + 1) * slide / speed_max, -(15 + 1) * slide / speed_max
db -(0 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(1 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(2 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(3 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(4 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(5 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(6 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(7 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(8 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(9 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(10 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(11 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(12 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(13 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(14 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db -(15 + 1) * (0 - 8) * (centrifuge / 8) / speed_max
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0
db 0, 0
db 0, 1
db 1, 0
db 1, 1
__sjasm_page_0_end:
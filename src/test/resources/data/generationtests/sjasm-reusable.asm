; Test to make sure reusable labels are parsed and generated correctly:
    add 10
    jr c, 1f
    ld a, (hl)
    jr 2f
1:    ld a, (bc)
2:    and #f0
loop:
    jr loop

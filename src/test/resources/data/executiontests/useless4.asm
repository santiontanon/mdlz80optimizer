
    org #4000

start:
    ld bc, 2100
    call one_over_bc_table_part5
    ld (#c000), hl
end:
    jp end


one_over_bc_table_part5:
    ; if bc == 2048, we want to access projection_table + 1280:
    ld a, b
    rrca
    rr c
    rrca
    rr c
    rrca
    rr c
    ld l, c
    ld h, (projection_table / 256) + 5
    ld l, (hl)
    ld h, 0
    ret


    org #c000
projection_table:
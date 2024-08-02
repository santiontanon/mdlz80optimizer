; Test case: to make sure a bug that was optimizing line 16 away in this test does not occur again.

start:
    call function1
    ld (v3), a
loop:
    jr loop


function1:
    add hl, de
    ld de, v1
    add hl, de
    ld a, (v2)
    ld (v3), a
    ld e, a
    ld a, (hl)
    inc a  ; cp #ff
    ret nz
    ld a, e
    ld (hl), a
    add a, 4
    jr nz, get_sprite_internal_no_loop
    ld a, 4
get_sprite_internal_no_loop:
    ret


v1: ds virtual 2
v2: ds virtual 1
v3: ds virtual 1

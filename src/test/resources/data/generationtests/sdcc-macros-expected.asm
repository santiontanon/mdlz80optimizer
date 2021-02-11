; Test case: Thanks to Bengalack for this test case to test asdz80 macros
___expanded_macro___1.0:
    halt
___expanded_macro___1.1:
    halt
    ld a, #0x01
    out (#0x99), a
    ld a, #0x00
    out (#0x99), a
    ld a, #2
    out (#0x99), a
    ld a, #(128 + 15)
    out (#0x99), a
___expanded_macro___1.110000:
    in a, (#0x99)
    and #1
    jp nz, ___expanded_macro___1.110000
    xor a
    out (#0x99), a
    ld a, #(128 + 15)
    out (#0x99), a
    ld a, #2
    out (#0x99), a
    ld a, #(128 + 15)
    out (#0x99), a
___expanded_macro___1.110001:
    in a, (#0x99)
    and #1
    jp nz, ___expanded_macro___1.110001
    xor a
    out (#0x99), a
    ld a, #(128 + 15)
    out (#0x99), a

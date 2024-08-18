; Test: test to make sure MDL knows that "cp a" does not depend on "a"

    ld a, 1
    cp a
loop:
    jr z,loop
    jr loop
    org 0x4000
label:
        db 20
label.#start:
    db 1, 1
label.#len: equ $ - label.#start
        db 20
___expanded_macro___2.label.#start:
    db 1, 1
___expanded_macro___2.label.#len: equ $ - ___expanded_macro___2.label.#start

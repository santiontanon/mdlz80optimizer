; Test case for device emulation and phase/dephase
   org #4000
   dw #0102
__sjasmplus_phase_pre_1:    org #4010
__sjasmplus_phase_post_1:
   dw #0304
__sjasmplus_dephase_1:    org __sjasmplus_phase_pre_1 + (__sjasmplus_dephase_1 - __sjasmplus_phase_post_1)
   org #4020
   dw #0506
   org #4030
   dw 772  ; this should be #0304
   dw 0  ; this should be #0000
   db 0  ; this should be #00
   dw 1286  ; this should be #0506
   db 6  ; this should be #06

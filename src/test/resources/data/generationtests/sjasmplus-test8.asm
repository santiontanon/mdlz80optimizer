; Test case for device emulation and phase/dephase
   output "sjasmplus-test8-expected.bin"

   device ZXSPECTRUM48

   org #4000
   dw #0102

   phase #4010
   dw #0304
   dephase

   org #4020
   dw #0506

   org #4030
   dw {#4002}  ; this should be #0304
   dw {#4010}  ; this should be #0000
   db {b #4010}  ; this should be #00
   dw {#4020}  ; this should be #0506
   db {b #4020}  ; this should be #06

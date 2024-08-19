  org $8000

  %macro  ips_patch_file %n,%n,%s   ; start address, maximum possible size, file location and name

    dw #1,.#len,#2,00h

  .#start:
    db #3
  .#len:  equ $ - .#start

  %endmacro

ips_patch_file 0x4000, 1024, "test string"

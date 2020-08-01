slide	= 8
centrifuge = 8
speed_max    = 16
go cp  speed_max*4
  ld  a,speed_max*4
repeat  speed_max
  byte  (@#+1)*slide/speed_max,-(@#+1)*slide/speed_max
endrepeat
repeat 1
[speed_max] byte  -(%+1)*(@#-8)*(centrifuge/8)/speed_max
endrepeat
[32-speed_max]  byte  0
[2][2] byte %,%%
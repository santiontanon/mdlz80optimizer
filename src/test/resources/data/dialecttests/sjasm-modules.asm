; Test case: 
label0
  module main
    jr label1
label1
	jr .local
.local
	jr sound.label3
  module vdp
label2
.local
  module sound
label3
  endmodule vdp
label4
  endmodule
label5
    org #0008

; This is the function called by "rst #08"
function08:
    ld a, 1
    ret  ; RET1 1

main:
    rst #00
    rst #08
loop:
    jp loop  ; RET1-DESTINATION


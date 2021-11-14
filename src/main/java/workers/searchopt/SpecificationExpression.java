/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.Expression;
import util.microprocessor.Z80.CPUConstants;

/**
 *
 * @author santi
 */
public class SpecificationExpression {
    public CPUConstants.RegisterNames leftRegister  = null;
    public Integer leftFlagIndex = null;
    public Integer leftConstantMemoryAddress = null;
    
    public Expression right;

    /*
    public boolean check(Z80Core z80, IMemory z80memory, CodeBase code)
    {
        int leftValue = z80.getRegisterValue(leftRegister);
        Integer rightValue = right.evaluateToInteger(null, code, true);
        if (rightValue == null) return false;
        switch(leftRegister) {
            case A:
            case F:
            case A_ALT:
            case F_ALT:
            case I:
            case R:
            case B:
            case C:
            case D:
            case E:
            case H:
            case L:
            case B_ALT:
            case C_ALT:
            case D_ALT:
            case E_ALT:
            case H_ALT:
            case L_ALT:
            case IXH:
            case IXL:
            case IYH:
            case IYL:
                return leftValue == (rightValue & 0xff);
            default:
                return leftValue == (rightValue & 0xffff);
        }
    }
    */

    @Override
    public String toString() {
        if (leftRegister != null) {
            return CPUConstants.registerName(leftRegister) + " = " + right;        
        } else if (leftFlagIndex != null) {
            return CPUConstants.flagName(leftFlagIndex) + " = " + right;
        } else if (leftConstantMemoryAddress != null) {
            return "(" + leftConstantMemoryAddress + ") = " + right;
        } else {
            return null;
        }        
    }    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import java.util.ArrayList;
import java.util.List;
import util.microprocessor.IMemory;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class Specification {    
    public static class SpecificationExpression {
        public CPUConstants.RegisterNames leftRegister;
        public Expression right;

        public String leftRegisterName; // just for the "toString" method
        
        
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
                    return leftValue == rightValue;
            }
        }
        
        
        @Override
        public String toString() {
            return leftRegisterName + " = " + right;
        }
    }
    
    int codeStartAddress = 0x4000;
    int maxSimulationTime = 256;
    
    // instruction set:
    boolean allowAndOrXorOps = true;
    boolean allowIncDecOps = true;
    boolean allowAddAdcSubSbc = true;
    
    public List<InputParameter> parameters = new ArrayList<>();
    public List<SpecificationExpression> startState = new ArrayList<>();
    public List<SpecificationExpression> goalState = new ArrayList<>();
    
    
    public void clearOpGroups()
    {
        allowAndOrXorOps = false;
        allowIncDecOps = false;
        allowAddAdcSubSbc = false;
    }
    
    
    public void addParameter(SourceConstant symbol)
    {
        for(InputParameter p:parameters) {
            if (p.symbol.name.equals(symbol.name)) return;
        }
        parameters.add(new InputParameter(symbol));
    }
    
    
    public InputParameter getParameter(String name) 
    {
        for(InputParameter p:parameters) {
            if (p.symbol.name.equals(name)) return p;
        }
        return null;
    }
    
    
    public boolean initCPU(Z80Core z80, CodeBase code)
    {
        for(SpecificationExpression exp:startState) {
            Integer value = exp.right.evaluateToInteger(null, code, true);
            if (value == null) return false;
            z80.setRegisterValue(exp.leftRegister, value);
        }
        return true;
    }
    
    public boolean checkGoalState(Z80Core z80, IMemory z80memory, CodeBase code)
    {
        for(SpecificationExpression exp:goalState) {
            if (!exp.check(z80, z80memory, code)) return false;
        }
        return true;
    }
    
    
    @Override
    public String toString() {
        String tmp = "start_state:\n";
        for(SpecificationExpression exp:startState) {
            tmp += exp + "\n";
        }
        tmp += "goal_state:\n";
        for(SpecificationExpression exp:goalState) {
            tmp += exp + "\n";
        }
        return tmp;
    }            
}

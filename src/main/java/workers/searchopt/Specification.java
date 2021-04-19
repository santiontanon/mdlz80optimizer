/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.Expression;
import java.util.ArrayList;
import java.util.List;
import util.microprocessor.Z80.CPUConstants;

/**
 *
 * @author santi
 */
public class Specification {
    public static class InputParameter {
        String name;
        int minValue = 0;
        int maxValue = 255;
        
        public InputParameter(String a_name, int a_minValue, int a_maxValue)
        {
            name = a_name;
            minValue = a_minValue;
            maxValue = a_maxValue;
        }        
    }
    
    
    public static class SpecificationExpression {
        public CPUConstants.RegisterNames leftRegister;
        public Expression right;

        public String leftRegisterName; // just for the "toString" method
        
        @Override
        public String toString() {
            return leftRegisterName + " = " + right;
        }
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
    
    
    // initial state:
    List<InputParameter> parameters = new ArrayList<>();
    List<SpecificationExpression> startState = new ArrayList<>();
    List<SpecificationExpression> goalState = new ArrayList<>();        
}

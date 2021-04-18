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
        CPUConstants.RegisterNames leftRegister;
        Expression right;
        
    }
    
    
    // initial state:
    List<InputParameter> parameters = new ArrayList<>();
    List<SpecificationExpression> initialState = new ArrayList<>();
    List<SpecificationExpression> goalState = new ArrayList<>();    
}

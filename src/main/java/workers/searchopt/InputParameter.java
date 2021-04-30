/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.SourceConstant;

/**
 *
 * @author santi
 */
public class InputParameter {
    SourceConstant symbol;
    int minValue = 0;
    int maxValue = 0xff;


    public InputParameter(SourceConstant a_symbol)
    {
        symbol = a_symbol;
    }        


    public InputParameter(SourceConstant a_symbol, int a_minValue, int a_maxValue)
    {
        symbol = a_symbol;
        minValue = a_minValue;
        maxValue = a_maxValue;
    }
}

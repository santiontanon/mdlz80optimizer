/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.SourceConstant;
import java.util.Random;

/**
 *
 * @author santi
 */
public class InputParameter {
    public static final int UNSIGNED = 0;
    public static final int SIGNED = 1;
    public static final int SIGNED_SAFE = 2;
    
    
    SourceConstant symbol;
    int minValue = 0;
    int maxValue = 0xff;
    int signed = UNSIGNED;


    public InputParameter(SourceConstant a_symbol, int a_signed)
    {
        symbol = a_symbol;
        signed = a_signed;
    }        


    public InputParameter(SourceConstant a_symbol, int a_minValue, int a_maxValue, int a_signed)
    {
        symbol = a_symbol;
        minValue = a_minValue;
        maxValue = a_maxValue;
        signed = a_signed;
    }
    
    
    public int randomValue(Random rand) throws Exception
    {
        int value = minValue + rand.nextInt((maxValue - minValue)+1);
        switch(signed) {
            case SIGNED:
            case SIGNED_SAFE:
                switch (maxValue) {
                    case 0xff:
                        value = eightBitValue(value, signed);
                        break;
                    case 0xffff:
                        value = sixteenBitValue(value, signed);
                        break;
                    default:
                        throw new Exception("Unsuported case: signed values with max value different from 0xff or 0xffff!");
                }
                break;                
        }
        return value;
    }
    
    
    public static int eightBitValue(int value, int signed)
    {
        value = value & 0xff;
        if (signed == SIGNED) {
            if (value >= 128) {
                value -= 256;
            }
        } else if (signed == SIGNED_SAFE) {
            if (value >= 128) {
                value -= 256;
            }
            if (value == -128) value = -127;
        }
        return value;
    }
    
    
    public static int sixteenBitValue(int value, int signed)
    {
        value = value & 0xffff;
        if (signed == SIGNED) {
            if (value >= 32768) {
                value -= 65536;
            }
        } else if (signed == SIGNED_SAFE) {
            if (value >= 32768) {
                value -= 65536;
            }
            if (value == -32768) value = -32767;
        }
        return value;
    }
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util.microprocessor;

/**
 *
 * @author santi
 */
public class PlainZ80IO implements IBaseDevice {
    @Override
    public int IORead(int address) 
    {
        return 0;
    }

    @Override
    public void IOWrite(int address, int data) 
    {
    }
}  
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
public class PlainZ80Memory implements IMemory {
    private final int[] memory = new int[0x10000];

    public PlainZ80Memory() {
    }

    @Override
    public int readByte(int address) {
        return memory[address];
    }

    @Override
    public int readWord(int address) {
        return readByte(address) + readByte(address + 1) * 256;
    }

    @Override
    public void writeByte(int address, int data) {

        memory[address] = data;
    }

    @Override
    public void writeWord(int address, int data) {
        writeByte(address, (data & 0x00ff));
        address = (address + 1) & 0xffff;
        data = (data >>> 8);
        writeByte(address, data);
    }
}
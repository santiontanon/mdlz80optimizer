/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util.microprocessor;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author santi
 */
public class PlainZ80Memory implements IMemory {    
    public final int[] memory = new int[0x10000];
    public List<Pair<Integer, Integer>> writeProtections = new ArrayList<>();

    public PlainZ80Memory() {
    }

    @Override
    final public int readByte(int address) {
        return memory[address];
    }

    @Override
    final public int readWord(int address) {
        return readByte(address) + readByte((address + 1) & 0xffff) * 256;
    }

    @Override
    final public void writeByte(int address, int data) 
    {
        for(Pair<Integer, Integer> p:writeProtections) {
            if (address >= p.getLeft() && address < p.getRight()) return;
        }
        memory[address] = data;
    }

    @Override
    final public void writeWord(int address, int data) {
        writeByte(address, (data & 0x00ff));
        address = (address + 1) & 0xffff;
        data = (data >>> 8);
        writeByte(address, data);
    }
    
    
    public void writeProtect(int start, int end)
    {
        writeProtections.add(Pair.of(start, end));
    }
    
    
    public void clearWriteProtections()
    {
        writeProtections.clear();
    }    
}
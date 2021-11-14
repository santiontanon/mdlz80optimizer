/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util.microprocessor;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author santi
 * 
 * This class tracks all the memory writes that happen during Z80 simulation.
 * This is useful for the search-based optimizer, to see if a program wrote
 * outside of the range that it was supposed to.
 * 
 */
public class TrackingZ80Memory implements IMemory {    
    public static final int MEMORY_SIZE = 0x10000;
    public final int[] memory;
    public List<Pair<Integer, Integer>> writeProtections = new ArrayList<>();
    List<Integer> memoryWrites = new ArrayList();

    public TrackingZ80Memory() {
        this.memory = new int[MEMORY_SIZE];
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
        memoryWrites.add(address);
        memory[address] = data;
    }

    @Override
    final public void writeWord(int address, int data) {
        writeByte(address, (data & 0x00ff));
        address = (address + 1) & 0xffff;
        data = (data >>> 8);
        writeByte(address, data);
    }
    
    
    @Override
    final public void writeProtect(int start, int end)
    {
        writeProtections.add(Pair.of(start, end));
    }
    
    
    @Override
    final public void clearWriteProtections()
    {
        writeProtections.clear();
    }    
    
    
    final public List<Integer> getMemoryWrites()
    {
        return memoryWrites;
    }
    
    
    final public void clearMemoryWrites()
    {
        memoryWrites.clear();
    }


    @Override
    final public int[] getMemoryArray()
    {
        return memory;
    }
}

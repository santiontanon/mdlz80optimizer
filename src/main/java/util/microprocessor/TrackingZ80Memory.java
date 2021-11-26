/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util.microprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    public static final int TRACKING_BUFFER = 32;
    
    public final int[] memory;
    ICPU cpu = null;
    
    public List<Pair<Integer, Integer>> writeProtections = new ArrayList<>();
    int memoryWritesIndex = 0;
    int memoryWriteAddresses[] = new int[TRACKING_BUFFER];
    int memoryWriteTime[] = new int[TRACKING_BUFFER];
    int memoryReadsIndex = 0;
    int memoryReadAddresses[] = new int[TRACKING_BUFFER];
    int memoryReadValues[] = new int[TRACKING_BUFFER];
    int memoryReadTime[] = new int[TRACKING_BUFFER];
    Random r = new Random();
    
    public TrackingZ80Memory(ICPU a_cpu) {
        memory = new int[MEMORY_SIZE];
        cpu = a_cpu;
    }
    
    public void setCPU(ICPU a_cpu)
    {
        cpu = a_cpu;
    }

    @Override
    final public int readByte(int address) {
        if (memoryReadsIndex<TRACKING_BUFFER) {
            memoryReadAddresses[memoryReadsIndex] = address;
            memoryReadValues[memoryReadsIndex] = memory[address];
            memoryReadTime[memoryReadsIndex] = (int)cpu.getTStates();
            memoryReadsIndex++;
        }
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
        if (memoryWritesIndex<TRACKING_BUFFER) {
            memoryWriteAddresses[memoryWritesIndex] = address;
            memoryWriteTime[memoryWritesIndex] = (int)cpu.getTStates();
            memoryWritesIndex++;
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
        List<Integer> writes = new ArrayList<>();
        for(int i = 0;i<memoryWritesIndex;i++) {
            writes.add(memoryWriteAddresses[i]);
        }
        return writes;
    }
    
    
    final public List<int[]> getMemoryReads()
    {
        List<int[]> reads = new ArrayList<>();
        for(int i = 0;i<memoryReadsIndex;i++) {
            reads.add(new int[]{memoryReadAddresses[i], memoryReadValues[i], (int)memoryReadTime[i]});
        }
        return reads;
    }


    final public void clearMemoryAccesses()
    {
        memoryWritesIndex = 0;
        memoryReadsIndex = 0;
    }
    
    
    final public void clearMemoryAccessesRandomizingThem(int protectStart, int protectEnd)
    {
        for(int i = 0;i<memoryWritesIndex;i++) {
            int address = memoryWriteAddresses[i];
            if (address < protectStart || address >= protectEnd) {                
                memory[address] = r.nextInt(256);
            }
        }
        for(int i = 0;i<memoryReadsIndex;i++) {
            int address = memoryReadAddresses[i];
            if (address < protectStart || address >= protectEnd) {  
                memory[address] = r.nextInt(256);
            }
        }
        memoryWritesIndex = 0;
        memoryReadsIndex = 0;
        
    }


    @Override
    final public int[] getMemoryArray()
    {
        return memory;
    }
    
    
    final public boolean writtenBefore(int address, int time)
    {
        for(int i = 0;i<memoryWritesIndex;i++) {
            if (memoryWriteAddresses[i] == address && 
                memoryWriteTime[i] < time) {
                return true;
            }
        }
        return false;
    }
}

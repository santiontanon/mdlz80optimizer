/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util.microprocessor;

import cl.MDLConfig;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class MappedTrackingZ80Memory implements IMemory {
    
    public static class MemorySegment {
        int source;
        int segment;
        
        public MemorySegment(int a_origin, int a_sn)
        {
            source = a_origin;
            segment = a_sn;
        }
    }
    
    public static class MemorySource {
        public int memory[];
        public int mapper;
        public boolean writeProtected = false;
        
        public MemorySource(int size, int a_mapper, boolean a_writeProtected) {
            memory = new int[size];
            mapper = a_mapper;
            writeProtected = a_writeProtected;
        }
    }
    
    public static final int VISIBLE_MEMORY_SIZE = 0x10000;
    public static final int TRACKING_BUFFER = 32;
    
    public static final int NO_MAPPER = 0;
    public static final int MSX_ASCII16_MAPPER = 1;
    
    public boolean infoPageChanges = false;
    MDLConfig config;
    
    public List<MemorySource> sources = new ArrayList<>();
    
    public int pageSize = 16 * 1024;
    public int nPages;
    public MemorySegment pages[];
    ICPU cpu = null;
    
    int memoryWritesIndex = 0;
    int memoryWriteAddresses[] = new int[TRACKING_BUFFER];
    int memoryWriteTime[] = new int[TRACKING_BUFFER];
    int memoryReadsIndex = 0;
    int memoryReadAddresses[] = new int[TRACKING_BUFFER];
    int memoryReadValues[] = new int[TRACKING_BUFFER];
    int memoryReadTime[] = new int[TRACKING_BUFFER];
    int nProtectedWrites = 0;
    
    
    public MappedTrackingZ80Memory(ICPU a_cpu, MDLConfig a_config, boolean a_infoPageChanges) {
        cpu = a_cpu;
        config = a_config;
        infoPageChanges = a_infoPageChanges;
        initMemory(NO_MAPPER);
    }

    public MappedTrackingZ80Memory(ICPU a_cpu, int a_pageSize, int mapper_type, MDLConfig a_config, boolean a_infoPageChanges) {
        pageSize = a_pageSize;
        cpu = a_cpu;
        config = a_config;
        infoPageChanges = a_infoPageChanges;
        initMemory(mapper_type);
    }

    final void initMemory(int mapper_type) {
        nPages = VISIBLE_MEMORY_SIZE / pageSize;
        sources.add(new MemorySource(VISIBLE_MEMORY_SIZE, mapper_type, false));
        pages = new MemorySegment[nPages];
        for(int i = 0;i<nPages;i++) {
            pages[i] = new MemorySegment(0, i);
        }
    }
    
    public void setCPU(ICPU a_cpu)
    {
        cpu = a_cpu;
    }
    
    public void addMemorySource(int size, int mapper, boolean writeProtected) throws Exception
    {
        sources.add(new MemorySource(size, mapper, writeProtected));
        if (mapper == MSX_ASCII16_MAPPER && pageSize != 16*1024) {
            throw new Exception("MSX_ASCII16_MAPPER requires pageSize == 16384, but was " + pageSize);
        }
    }
    
    public void mapPage(int page, int source, int segment)
    {
        pages[page].source = source;
        pages[page].segment = segment;
    }

    @Override
    public int readByte(int address) {
        int page = address / pageSize;
        int withinPage = address % pageSize;
        MemorySegment s = pages[page];
        int v = sources.get(s.source).memory[s.segment * pageSize + withinPage];
        if (memoryReadsIndex < TRACKING_BUFFER) {
            memoryReadAddresses[memoryReadsIndex] = address;
            memoryReadValues[memoryReadsIndex] = v;
            memoryReadTime[memoryReadsIndex] = (int)cpu.getTStates();
            memoryReadsIndex++;
        }
        return v;
    }

    @Override
    public int readWord(int address) {
        return readByte(address) + readByte((address + 1) & 0xffff) * 256;
    }

    @Override
    public void writeByte(int address, int data) 
    {
        int page = address / pageSize;
        int withinPage = address % pageSize;
        MemorySegment s = pages[page];
        MemorySource ms = sources.get(s.source);
        
        switch(ms.mapper) {
            case MSX_ASCII16_MAPPER:
                if (address == 0x6000) {
                    // Switch page:
                    mapPage(1, s.source, data);
                    if (infoPageChanges) {
                        config.info("MappedTrackingZ80Memory: MSX_ASCII16_MAPPER: page 1 to segment " + data);
                    }
                    return;
                } else if (address == 0x7000) {
                    // Switch page:
                    mapPage(2, s.source, data);
                    if (infoPageChanges) {
                        config.info("MappedTrackingZ80Memory: MSX_ASCII16_MAPPER: page 1 to segment " + data);
                    }
                    return;                    
                }
                break;
        }
        
        if (ms.writeProtected) {
            config.warn("MappedTrackingZ80Memory: Writing to a write protected source: " + s.source + ", address: " + config.tokenizer.toHexWord(address, config.hexStyle) + ", value: " + data);
            nProtectedWrites++;
            return;
        }
        
        if (memoryWritesIndex < TRACKING_BUFFER) {
            memoryWriteAddresses[memoryWritesIndex] = address;
            memoryWriteTime[memoryWritesIndex] = (int)cpu.getTStates();
            memoryWritesIndex++;
        }
        ms.memory[s.segment * pageSize + withinPage] = data;
    }
    
    public void writeByteToSource(int source, int address, int data)
    {
        sources.get(source).memory[address] = data;
    }

    @Override
    public void writeWord(int address, int data) {
        writeByte(address, (data & 0x00ff));
        address = (address + 1) & 0xffff;
        data = (data >>> 8);
        writeByte(address, data);
    }
    
    
    @Override
    public void writeProtect(int start, int end) throws Exception
    {
        throw new Exception("writeProtect not implemented in MappedTrackingZ80Memory");
    }
    
    
    @Override
    public void clearWriteProtections() throws Exception
    {
        throw new Exception("clearWriteProtections not implemented in MappedTrackingZ80Memory");
    }    
    
    
    public List<Integer> getMemoryWrites()
    {
        List<Integer> writes = new ArrayList<>();
        for(int i = 0;i<memoryWritesIndex;i++) {
            writes.add(memoryWriteAddresses[i]);
        }
        return writes;
    }
    
    
    public List<int[]> getMemoryReads()
    {
        List<int[]> reads = new ArrayList<>();
        for(int i = 0;i<memoryReadsIndex;i++) {
            reads.add(new int[]{memoryReadAddresses[i], memoryReadValues[i], (int)memoryReadTime[i]});
        }
        return reads;
    }


    public void clearMemoryAccesses()
    {
        memoryWritesIndex = 0;
        memoryReadsIndex = 0;
        nProtectedWrites = 0;
    }


    @Override
    public int[] getMemoryArray() throws Exception
    {
        throw new Exception("getMemoryArray not implemented in MappedTrackingZ80Memory");
    }
    
    
    public boolean writtenBefore(int address, int time)
    {
        for(int i = 0;i<memoryWritesIndex;i++) {
            if (memoryWriteAddresses[i] == address && 
                memoryWriteTime[i] < time) {
                return true;
            }
        }
        return false;
    }    
    
    
    public String addressString(int address)
    {
        int page = address / pageSize;
        int withinPage = address % pageSize;
        MemorySegment s = pages[page];
        return s.source + ":" + ((s.segment * pageSize) + withinPage);
    }
    
    
    public Integer integerAddressOf(String addressString)
    {
        String tokens[] = addressString.split(":");
        if (tokens.length == 1) {
            return Integer.parseInt(tokens[0]);
        } else if (tokens.length == 2) {
            int source = Integer.parseInt(tokens[0]);
            int address = Integer.parseInt(tokens[1]);
            int segment = address / pageSize;
            for(int i = 0;i<nPages;i++) {
                if (pages[i].source == source && pages[i].segment == segment) {
                    return i * pageSize + (address % pageSize);
                }
            }
        }
        return null;
    }
    
    
    public int getNProtectedWrites()
    {
        return nProtectedWrites;
    }
}

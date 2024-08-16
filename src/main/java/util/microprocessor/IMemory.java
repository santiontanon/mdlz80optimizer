/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * - Original code borrowed from https://github.com/codesqueak/Z80Processor
 * - Modifications to integrate into MDL by Santiago Ontañón
 */

package util.microprocessor;

/**
 * Interface to describe the memory processor bus
 */
public interface IMemory {

    /**
     * Read a byte from memory
     *
     * @param address The address to read from
     * @return The byte read
     */
    int readByte(int address);

    
    /**
     * Read a byte from memory (but do not track it in implementations that 
     * track).
     *
     * @param address The address to read from
     * @return The byte read
     */
    int readByteUntracked(int address);
    

    /**
     * Read a 16 bit word from memory, LSB, MSB order
     *
     * @param address The address to read from
     * @return The word read
     */
    int readWord(int address);

    /**
     * Write a byte into memory
     *
     * @param address The address to be written to
     * @param data    The byte to be written
     */
    void writeByte(int address, int data);

    /**
     * Write a 16 bit word into memory, LSB, MSB order.
     *
     * @param address The address to be written to
     * @param data    The word to be written
     */
    void writeWord(int address, int data);
    
    
    default void writeProtect(int start, int end) throws Exception
    {
    }
    
    
    default void clearWriteProtections() throws Exception
    {    
    }
    
    
    public int[] getMemoryArray() throws Exception;
}
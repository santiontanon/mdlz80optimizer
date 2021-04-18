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
 * Interface to describe the I/O processor bus
 */
public interface IBaseDevice {

    /**
     * Read data from an I/O port
     *
     * @param address The port to be read from
     * @return The 8 bit value at the request port address
     */
    default int IORead(int address) {
        return 0;
    }

    /**
     * Write data to an I/O port
     *
     * @param address The port to be written to
     * @param data    The 8 bit value to be written
     */
    default void IOWrite(int address, int data) {
        // do nothing
    }
}
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
 * Interface to the processor
 */
public interface ICPU {

    /**
     * Reset the processor to a known state. Equivalent to a hardware reset.
     */
    void reset();

    /**
     * Returns the state of the halt flag
     *
     * @return True if the processor has executed a HALT instruction
     */
    boolean getHalt();

    /**
     * Execute a single instruction at the present program counter (PC) then return. The internal state of the processor
     * is updated along with the T state count.
     *
     * @throws ProcessorException Thrown if an unexpected state arises
     */
    void executeOneInstruction() throws ProcessorException;

    /**
     * Return the number of T states since last reset
     *
     * @return Processor T states
     */
    long getTStates();

    /**
     * Reset the T state counter to zero
     */
    void resetTStates();
}

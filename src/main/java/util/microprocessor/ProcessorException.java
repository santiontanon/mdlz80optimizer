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
 * Exception thrown under various CPU states that may need exception processing
 */
public class ProcessorException extends Exception {

    public final static String COMPUTER_UNIMPLEMENTED_OPCODE = "Unimplemented opcode detected";
    private static final long serialVersionUID = 3257847701181118519L;

    /**
     * Known exception for the Z80 emulator
     *
     * @param msg Emulator exception message
     */
    public ProcessorException(String msg) {
        super(msg);
    }
}
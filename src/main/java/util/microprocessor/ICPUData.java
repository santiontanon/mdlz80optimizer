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
 * Interface to describe the processor version
 */
public interface ICPUData {

    /**
     * Get the processor major CPU version number
     *
     * @return major revision number
     */
    String getMajorVersion();

    /**
     * Get the processor major CPU minor number
     *
     * @return minor revision number
     */
    String getMinorVersion();

    /**
     * Get the processor major CPU patch number
     *
     * @return patch number
     */
    String getPatchVersion();

    /**
     * Get the CPU name string
     *
     * @return name string
     */
    String getName();
}

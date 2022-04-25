/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import util.microprocessor.ProcessorException;

/**
 *
 * @author santi
 */
public interface PrecomputedTestCaseGenerator {
    PrecomputedTestCase generateTestCase(MDLConfig config) throws ProcessorException;
}

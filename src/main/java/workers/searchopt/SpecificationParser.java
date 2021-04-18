/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import java.io.BufferedReader;
import util.Resources;

/**
 *
 * @author santi
 */
public class SpecificationParser {
    public static Specification parse(String inputFile, MDLConfig config)
    {
        try (BufferedReader br = Resources.asReader(inputFile)) {
            Specification spec = new Specification();
            
            // ...
            
            return spec;
        } catch (Exception e) {
            config.error("Exception while tryint to parse specificaiton file '"+inputFile+"'");
            config.error("Exception message: " + e.getMessage());
            return null;
        }
    }
}

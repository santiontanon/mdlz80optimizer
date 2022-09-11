/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers;

import cl.MDLConfig;
import cl.MDLLogger;
import code.CodeBase;
import java.util.List;

/**
 *
 * @author santi
 */
public class Help implements MDLWorker {
    
    MDLConfig config = null;
    public boolean showMDTags = false;
//    public boolean simple = false;
    
    
    public Help(MDLConfig a_config) {
        config = a_config;
    }
    
            
    public String docString() {
        // The help string for this worker is built-in into the main docstring (MDLConfig.java),
        // so that it appears at the top.
        return "";
    }
    

    @Override
    public String simpleDocString() {
        // The help string for this worker is built-in into the main docstring (MDLConfig.java),
        // so that it appears at the top.
        return "";
    }
    

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-help")) {
            flags.remove(0);
            config.help_triggered = true;
            return true;
        } else if (flags.get(0).equals("-helpmd")) {
            flags.remove(0);
            // This is a hidden flag, as it has no utility for the user, and is only used
            // to generate the documentation for GitHub:
            showMDTags = true;
            config.help_triggered = true;
            return true;
        }
        return false;
    }
    

    @Override
    public boolean triggered() {
        return config.help_triggered;
    }
    

    @Override
    public boolean work(CodeBase code) {
        String docString = config.docString;
        if (config.display_simple_help) docString = config.simpleDocString;
        if (config.display_nothing_to_do_warning) {
            config.warn("Nothing to do. Please specify some task for MDL to do.");            
        }
        if (showMDTags) {
            config.info(docString);
        } else {
            config.info(MDtoHelpString(docString));
        }
        return true;
    }
 
    
    // Removes .md annotations (used for the GitHub documentation), for printing on the console:
    private String MDtoHelpString(String text)
    {
        String lines[] = text.split("\n");
        for(int i = 0;i<lines.length;i++) {
            String line = lines[i];
            if (line.startsWith("- ```")) {
                line = "- " + MDLLogger.ANSI_WHITE + line.substring(5);
                int idx = line.indexOf("```");
                if (idx >= 0) {
                    line = line.substring(0, idx) + MDLLogger.ANSI_RESET + line.substring(idx+3);
                }
                lines[i] = line;
            }
        }
        text = String.join("\n", lines);
        return text.replace("- ```-", "  -").replace("```", "");
    }    
}

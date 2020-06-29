/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.Expression;
import java.util.ArrayList;
import java.util.List;

public class SourceMacro {
    public static final String MACRO_MACRO = "macro";
    public static final String MACRO_ENDM = "endm";
    public static final String MACRO_REPT = "rept";
    public static final String MACRO_IF = "if";
    public static final String MACRO_ELSE = "else";
    public static final String MACRO_ENDIF = "endif";
    
    
    public String name = null;
    List<String> argNames = new ArrayList<>();
    List<String> lines = new ArrayList<>();
    List<String> elseLines = new ArrayList<>();  // only used by IF-ELSE-ENDIF macro
    
    // predefined macro arguments/state:
    public int reptNRepetitions;
    public int ifCondition;
    public boolean insideElse = false;
    
    
    public SourceMacro(String a_name)
    {
        name = a_name;
    }
    
    
    public SourceMacro(String a_name, List<String> a_args)
    {
        name = a_name;
        argNames = a_args;
    }


    public void addLine(String line)
    {
        if (insideElse) {
            elseLines.add(line);
        } else {
            lines.add(line);
        }
    }
    
    
    public List<String> instantiate(List<Expression> args, MDLConfig config)
    {
        List<String> lines2 = new ArrayList<>();
        if (name.equalsIgnoreCase(MACRO_REPT)) {
            for(int i = 0;i<reptNRepetitions;i++) {
                lines2.addAll(lines);
            }
        } else if (name.equalsIgnoreCase(MACRO_IF)) {
            if (ifCondition == 0) {
                lines2.addAll(elseLines);
            } else {
                lines2.addAll(lines);
            }
        } else {
            // instantiate arguments:
            if (argNames.size() != args.size()) {
                config.error("Number of parameters in macro call incorrect!");
                return null;
            }
            for(String line:lines) {
                String line2 = line;
                for(int i = 0;i<argNames.size();i++) {
                    line2 = line2.replace("?" + argNames.get(i), 
                                          args.get(i).toString());
                }
                lines2.add(line2);
            }            
        }
        return lines2;
    }
}

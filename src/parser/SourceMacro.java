/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceStatement;
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
    public SourceStatement definingStatement = null;
    public List<String> argNames = new ArrayList<>();
    public List<Expression> defaultValues = new ArrayList<>();
    List<String> lines = new ArrayList<>();
    List<String> elseLines = new ArrayList<>();  // only used by IF-ELSE-ENDIF macro
    
    // predefined macro arguments/state:
    public Expression reptNRepetitions;
    public Expression ifCondition;
    public boolean insideElse = false;
    
    
    public SourceMacro(String a_name, SourceStatement a_ds)
    {
        name = a_name;
        definingStatement = a_ds;
    }
    
    
    public SourceMacro(String a_name, List<String> a_args, List<Expression> a_defaultValues, SourceStatement a_ds)
    {
        name = a_name;
        argNames = a_args;
        defaultValues = a_defaultValues;
        definingStatement = a_ds;
    }


    public void addLine(String line)
    {
        if (insideElse) {
            elseLines.add(line);
        } else {
            lines.add(line);
        }
    }
    
    
    public List<String> instantiate(List<Expression> args, CodeBase code, MDLConfig config)
    {
        List<String> lines2 = new ArrayList<>();
        if (name.equalsIgnoreCase(MACRO_REPT)) {
            Integer reptNRepetitions_value = reptNRepetitions.evaluate(definingStatement, code, false);
            if (reptNRepetitions_value == null) {
                config.error("Coudl not evaluate REPT argument " + reptNRepetitions);
                return null;
            }
            for(int i = 0;i<reptNRepetitions_value;i++) {
                lines2.addAll(lines);
            }
        } else if (name.equalsIgnoreCase(MACRO_IF)) {
            Integer ifCondition_value = ifCondition.evaluate(definingStatement, code, false);
            if (ifCondition_value == null) {
                config.error("Coudl not evaluate IF argument " + ifCondition);
                return null;
            }
            if (ifCondition_value == 0) {
                lines2.addAll(elseLines);
            } else {
                lines2.addAll(lines);
            }
        } else {
            // instantiate arguments:
            while(args.size() < argNames.size()) {
                Expression defaultValue = defaultValues.get(args.size());
                if (defaultValue == null) {
                    config.error("Number of parameters in macro call incorrect!");
                    return null;                    
                } else {
                    args.add(defaultValue);
                }
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

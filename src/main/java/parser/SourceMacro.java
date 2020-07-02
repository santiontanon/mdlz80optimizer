/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceStatement;

public class SourceMacro {
    public static final String MACRO_MACRO = "macro";
    public static final String MACRO_ENDM = "endm";
    public static final String MACRO_REPT = "rept";
    public static final String MACRO_ENDR = "endr";
    public static final String MACRO_IF = "if";
    public static final String MACRO_IFDEF = "ifdef";
    public static final String MACRO_ELSE = "else";
    public static final String MACRO_ENDIF = "endif";


    public String name = null;
    public SourceStatement definingStatement = null;
    public List<String> argNames = new ArrayList<>();
    public List<Expression> defaultValues = new ArrayList<>();
    // line + lineNumber:
    List<Pair<String,Integer>> lines = new ArrayList<>();
    List<Pair<String,Integer>> elseLines = new ArrayList<>();  // only used by IF-ELSE-ENDIF macro

    // predefined macro arguments/state:
    public List<Expression> preDefinedMacroArgs = null;
//    public Expression reptNRepetitions;
//    public Expression ifCondition;
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


    public void addLine(String line, Integer lineNumber)
    {
        if (insideElse) {
            elseLines.add(Pair.of(line, lineNumber));
        } else {
            lines.add(Pair.of(line, lineNumber));
        }
    }


    public List<Pair<String,Integer>> instantiate(List<Expression> args, CodeBase code, MDLConfig config)
    {
        List<Pair<String,Integer>> lines2 = new ArrayList<>();
        if (name.equalsIgnoreCase(MACRO_REPT)) {
            Integer reptNRepetitions_value = args.get(0).evaluate(definingStatement, code, false);
            if (reptNRepetitions_value == null) {
                config.error("Could not evaluate REPT argument " + args.get(0));
                return null;
            }
            for(int i = 0;i<reptNRepetitions_value;i++) {
                lines2.addAll(lines);
            }
        } else if (name.equalsIgnoreCase(MACRO_IF)) {
            Integer ifCondition_value = args.get(0).evaluate(definingStatement, code, false);
            if (ifCondition_value == null) {
                config.error("Could not evaluate IF argument " + args.get(0));
                return null;
            }
            if (ifCondition_value == Expression.FALSE) {
                lines2.addAll(elseLines);
            } else {
                lines2.addAll(lines);
            }
        } else if (name.equalsIgnoreCase(MACRO_IFDEF)) {
            Expression exp = args.get(0);
            boolean defined = false;
            if (exp.type == Expression.EXPRESSION_SYMBOL) {
                if (code.getSymbol(exp.symbolName) != null) defined = true;
            } else {
                config.error("Incorrect parameter to " + MACRO_IFDEF + ": " + args.get(0));
                return null;
            }
            if (defined) {
                lines2.addAll(lines);
            } else {
                lines2.addAll(elseLines);
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
            for(Pair<String,Integer> line_lnumber:lines) {
                String line2 = line_lnumber.getLeft();
                for(int i = 0;i<argNames.size();i++) {
                    line2 = line2.replace("?" + argNames.get(i),
                                          args.get(i).toString());
                }
                lines2.add(Pair.of(line2, line_lnumber.getRight()));
            }
        }

        return lines2;
    }
}

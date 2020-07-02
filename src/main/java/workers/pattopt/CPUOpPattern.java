/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.List;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class CPUOpPattern {
    int ID;
    String opName;
    List<Expression> args = new ArrayList<>();


    public String toString()
    {
        String str = opName;
        boolean first = true;
        for(Expression arg:args) {
            if (first) {
                str += " " + arg;
                first = false;
            } else {
                str += ", " + arg;
            }
        }
        return str;
    }


    public CPUOp instantiate(PatternMatch match, MDLConfig config)
    {
        // replace variables by the matched values:
        CodeBase code = new CodeBase(config);
        SourceFile f = new SourceFile("", null, null, config);
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_CPUOP, f, 0, 0);
        List<Expression> instantiatedArgs = new ArrayList<>();
        for(Expression arg:args) {
            String argStr = arg.toString();
            for(String variable:match.variablesMatched.keySet()) {
                argStr = argStr.replace(variable, match.variablesMatched.get(variable).toString());
            }
            instantiatedArgs.add(config.expressionParser.parse(Tokenizer.tokenize(argStr), code));
        }

        return config.opParser.parseOp(opName, instantiatedArgs, s, code);
    }


    public static CPUOpPattern parse(String line, CodeBase code, MDLConfig config)
    {
        List<String> tokens = Tokenizer.tokenize(line);
        CPUOpPattern pat = new CPUOpPattern();
        pat.ID = Integer.parseInt(tokens.remove(0));
        if (!tokens.remove(0).equals(":")) {
            config.error("Cannot parse CPUOpPattern: " + line);
            return null;
        }
        pat.opName = tokens.remove(0);
        while(!tokens.isEmpty()) {
            if (tokens.get(0).startsWith(";")) break;
            Expression exp = config.expressionParser.parse(tokens, code);
            if (exp == null) {
                config.error("Cannot parse CPUOpPattern: " + line);
                return null;
            } else {
                pat.args.add(exp);
            }
            if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                tokens.remove(0);
            } else {
                break;
            }
        }
        return pat;
    }
}

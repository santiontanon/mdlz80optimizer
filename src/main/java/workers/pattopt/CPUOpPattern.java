/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import code.CPUOp;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import parser.SourceLine;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class CPUOpPattern {
    public static final String WILDCARD = "*";
    
    int ID;
    boolean wildcard = false;   // if this is true, "opName" and "args" will be "*" and empty.
    String opName;
    List<Expression> args = new ArrayList<>();


    @Override
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

    
    public boolean isWildcard()
    {
        return opName.equals(WILDCARD);
    }
    

    public CPUOp instantiate(PatternMatch match, Pattern pattern, MDLConfig config)
    {
        // replace variables by the matched values:
        CodeBase code = new CodeBase(config);
        SourceFile f = new SourceFile("", null, null, config);
        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_CPUOP, new SourceLine("", f, 0), f, 0);
        List<Expression> instantiatedArgs = new ArrayList<>();
        for(Expression arg:args) {
            String argStr = arg.toString();
            for(String variable:match.variables.keySet()) {
                argStr = argStr.replace(variable, match.variables.get(variable).toString());
            }
            Expression exp = config.expressionParser.parse(Tokenizer.tokenize(argStr), null, code);
            if (exp == null) {
                config.error("Cannot parse argument '" + argStr + "' when instantiating pattern " + pattern.name);
                return null;
            }
            instantiatedArgs.add(exp);
        }

        CPUOp op =  config.opParser.parseOp(opName, instantiatedArgs, s, code);
        if (op == null) {
            config.error("Cannot parse: " + opName + " " + instantiatedArgs);
        }
        return op;
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
        if (pat.opName.equals(WILDCARD)) return pat;
        while(!tokens.isEmpty()) {
            if (Tokenizer.isSingleLineComment(tokens.get(0))) break;
            Expression exp = config.expressionParser.parse(tokens, null, code);
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

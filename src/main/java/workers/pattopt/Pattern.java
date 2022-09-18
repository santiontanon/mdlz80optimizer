/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpDependency;
import code.CPUOpSpecArg;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.CodeStatement;
import java.util.Arrays;
import parser.SourceLine;

/**
 *
 * @author santi
 */
public class Pattern {
    public static class Constraint {
        public String name;
        public String args[];
        
        // If this is != -1, this constraint will be checked as soon as a
        // CPUOpPattern with ID == triggerAfterID is matched
        public int triggerAfterID = -1;
        
        public Constraint(String a_name, String a_args[], int a_triggerAfterID) {
            name = a_name;
            args = a_args;
            triggerAfterID = a_triggerAfterID;
        }
        
        public Constraint(Constraint c)
        {
            name = c.name;
            args = new String[c.args.length];
            for(int i = 0;i<args.length;i++) {
                args[i] = c.args[i];
            }
            triggerAfterID = c.triggerAfterID;
        }
        
        
        @Override
        public String toString()
        {
            return name + Arrays.toString(args);
        }
    }
    
    MDLConfig config;

    public String name = null;
    public String message = null;
    public List<String> tags = new ArrayList<>();    
    public List<CPUOpPattern> pattern = new ArrayList<>();
    public List<CPUOpPattern> replacement = new ArrayList<>();
    public List<Constraint> constraints = new ArrayList<>();
    
    
    static class DepCheckNode {
        CodeStatement s;
        CPUOpDependency dep;
        List<CodeStatement> callStack;
        
        public DepCheckNode(CodeStatement a_s, CPUOpDependency a_dep, List<CodeStatement> a_cs)
        {
            s = a_s;
            dep = a_dep;
            callStack = a_cs;
        }
        
        
        public boolean match(CPUOpDependency a_dep, List<CodeStatement> a_cs)
        {
            if (!a_dep.equals(dep)) return false;
            if (callStack == null) {
                if (a_cs != null) return false;
            } else {
                if (a_cs == null) return false;
                if (a_cs.size() != callStack.size()) return false;
                for(int i = 0;i<callStack.size();i++) {
                    if (callStack.get(i) != a_cs.get(i)) return false;
                }
            }
            return true;
        }
    }
        
    
    public Pattern(String patternString, MDLConfig a_config)
    {
        config = a_config;
        int state = 0;  // 0: default, 1: expecting pattern, 2: expecting replacement, 3: expecting constraints
        CodeBase patternCB = new CodeBase(config);

        // parseArgs the pattern:
        String lines[] = patternString.split("\n");
        for(String line:lines) {
            line = line.trim();
            if (line.startsWith("pattern:")) {
                message = line.substring(8).trim();
                state = 1;
            } else if (line.startsWith("name:")) {
                name = line.substring(5).trim();
            } else if (line.startsWith("tags:")) {
                tags.addAll(Arrays.asList(line.substring(5).split(" ")));
            } else if (line.equals("replacement:")) {
                state = 2;
            } else if (line.equals("constraints:")) {
                state = 3;
            } else {
                switch(state) {
                    case 1: // pattern:
                    {
                        CPUOpPattern patt = CPUOpPattern.parse(line, patternCB, config);
                        if (patt != null) {
                            pattern.add(patt);
                        } else {
                            config.error("Cannot parse pattern line: " + line);
                        }
                        break;
                    }

                    case 2: // replacement:
                    {
                        CPUOpPattern patt = CPUOpPattern.parse(line, patternCB, config);
                        if (patt != null) {
                            replacement.add(patt);
                        } else {
                            config.error("Cannot parse replacement line: " + line);
                        }
                        break;
                    }
                    case 3: // constraints:
                    {
                        List<String> tokens = config.tokenizer.tokenize(line);
                        String name = tokens.remove(0);
                        int triggerAfterID = -1;
                        List<Expression> expressions = new ArrayList<>();
                        if (!tokens.get(0).equals("(")) throw new RuntimeException("cannot parse constraint: " + line);
                        tokens.remove(0);
                        while(!tokens.get(0).equals(")")) {
                            Expression exp = config.expressionParser.parse(tokens, null, null, patternCB);
                            if (exp == null) throw new RuntimeException("cannot parse constraint: " + line);
                            expressions.add(exp);
                            if (tokens.get(0).equals(",")) tokens.remove(0);
                        }
                        tokens.remove(0);
                        
                        if (!tokens.isEmpty()) {
                            if (tokens.get(0).equals(":")) {
                                tokens.remove(0);
                                if (tokens.isEmpty()) {
                                    throw new RuntimeException("cannot parse constraint: " + line);
                                }
                                triggerAfterID = Integer.parseInt(tokens.remove(0));
                            } else {
                                throw new RuntimeException("cannot parse constraint: " + line);
                            }
                        } 
                        
                        String split[] = new String[expressions.size()];
                        for(int i = 0;i<split.length;i++) {
                            split[i] = expressions.get(i).toString();
                        }
                        constraints.add(new Constraint(name, split, triggerAfterID));
                        break;
                    }
                    default:
                        throw new RuntimeException("Unexpected line parsing a pattern: " + line);
                }
            }
        }
        
        // config.trace("parsed pattern: " + message);
    }
    
    
    public Pattern(Pattern p)
    {
        config = p.config;
        name = p.name;
        message = p.message;
        tags = p.tags;
        pattern = new ArrayList<>();
        for(CPUOpPattern opp:p.pattern) {
            pattern.add(new CPUOpPattern(opp));
        }
        replacement = new ArrayList<>();
        for(CPUOpPattern opp:p.replacement) {
            replacement.add(new CPUOpPattern(opp));
        }
        constraints = new ArrayList<>();
        for(Constraint c:p.constraints) {
            constraints.add(new Constraint(c));
        }
    }    


    public String getName()
    {
        return message;
    }
        
    
    public String getInstantiatedName(PatternMatch match)
    {
        String tmp = message;
        for(String variable:match.variables.keySet()) {
            tmp = tmp.replace(variable, match.variables.get(variable).toString());
        }
        return tmp;
    }
    
    
    public Pattern assignVariable(String name, String replacement, CodeBase code)
    {
        Pattern p = new Pattern(this);
        
        for(CPUOpPattern opp:p.pattern) {
            opp.assignVariable(name, replacement, code, config);
        }
        for(CPUOpPattern opp:p.replacement) {
            opp.assignVariable(name, replacement, code, config);
        }
        List<Constraint> todelete = new ArrayList<>();
        for(Constraint c:p.constraints) {
            if (c.name.equals("in") && c.args[0].equals(name)) {
                // remove this constraint:
                todelete.add(c);
            } else {
                for(int i = 0;i<c.args.length;i++) {
                    if (c.args[i].equals(name)) {
                        c.args[i] = replacement;
                    }
                }
            }
        }
        p.constraints.removeAll(todelete);
        return p;
    }


    public int getSpaceSaving(PatternMatch match, CodeBase code)
    {
        int patternSize = 0;
        int replacementSize = 0;
        for(CPUOpPattern pat:pattern) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, this, config);
                int ipatSize = ipat.sizeInBytes();
                int n = 1;
                if (pat.repetitionVariable != null) {
                    n = match.variables.get(pat.repetitionVariable).evaluateToInteger(null, code, true);
                }
                patternSize += n * ipatSize;
            }
        }
        for(CPUOpPattern pat:replacement) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, this, config);
                if (ipat == null) {
                    config.error("An optimization pattern ("+this.name+") has generated ilegal code!");
                } else {
                    int irepSize = ipat.sizeInBytes();
                    int n = 1;
                    if (pat.repetitionVariable != null) {
                        n = match.variables.get(pat.repetitionVariable).evaluateToInteger(null, code, true);
                    }
                    replacementSize += n * irepSize;
                }
            }
        }
        int spaceSaving = patternSize - replacementSize;
        return spaceSaving;
    }
    
    
    public int[] getTimeSaving(PatternMatch match, CodeBase code)
    {
        int patternTime[] = {0,0};
        int replacementTime[] = {0,0};
        for(CPUOpPattern pat:pattern) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, this, config);
                if (ipat == null) {
                    config.error("An optimization pattern ("+this.name+") has generated ilegal code!");
                    continue;
                } 
                int tmp[] = ipat.timing();
                int n = 1;
                if (pat.repetitionVariable != null) {
                    n = match.variables.get(pat.repetitionVariable).evaluateToInteger(null, code, true);
                }
                patternTime[0] += n * tmp[0];
                if (tmp.length>1) {
                    patternTime[1] += n * tmp[1];
                } else {
                    patternTime[1] += n * tmp[0];
                }
            }
        }
        for(CPUOpPattern pat:replacement) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, this, config);
                if (ipat == null) {
                    config.error("An optimization pattern ("+this.name+") has generated ilegal code!");
                    continue;
                } 
                int tmp[] = ipat.timing();
                int n = 1;
                if (pat.repetitionVariable != null) {
                    n = match.variables.get(pat.repetitionVariable).evaluateToInteger(null, code, true);
                }
                replacementTime[0] += n * tmp[0];
                if (tmp.length>1) {            
                    replacementTime[1] += n * tmp[1];
                } else {
                    replacementTime[1] += n * tmp[0];
                }
            }
        }
        return new int[]{patternTime[0] - replacementTime[0],
                         patternTime[1] - replacementTime[1]};
    }
    
    
    public String getTimeSavingString(PatternMatch match, CodeBase code)
    {
        int tmp[] = getTimeSaving(match, code);
        if (tmp[0] == tmp[1]) {
            return ""+tmp[0];
        } else {
            return tmp[0] + "/" + tmp[1];
        }
    }
    
    
    public boolean unifyExpressions(Expression pattern, Expression arg2, boolean expressionRoot, PatternMatch match, CodeStatement s, CodeBase code)
    {
        if (pattern.type == Expression.EXPRESSION_SYMBOL &&
            pattern.symbolName.startsWith("?")) {
            // it's a variable!
            if (pattern.symbolName.startsWith("?reg")) {
                if (arg2.isRegister()) {
                    return match.addVariableMatch(pattern.symbolName, arg2);
                } else {
                    return false;
                }
            } else if (pattern.symbolName.startsWith("?const")) {
                // We exclude matches with "parenthesis" expressions, as those might be indirections
                if (arg2.evaluatesToIntegerConstant() &&
                    arg2.type != Expression.EXPRESSION_PARENTHESIS) {
                    return match.addVariableMatch(pattern.symbolName, arg2);
                } else {
                    return false;
                }
            } else if (pattern.symbolName.startsWith("?any")) {
                return match.addVariableMatch(pattern.symbolName, arg2);
            } else {
                throw new RuntimeException("opMatch: unrecognized variable name " + pattern.symbolName);
            }     
        }

        if (pattern.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            // if the pattern is a numeric constant, and the argument is an expression that
            // evaluates to a number, the evaluateToInteger to check equality:
            // An exception is the "parenthesis" operation, which we assume is for an indirection if we
            // are at the top level of the expression
            if (arg2.evaluatesToIntegerConstant()) {
                if (!expressionRoot || arg2.type != Expression.EXPRESSION_PARENTHESIS) {
                    Integer arg2_val = arg2.evaluateToInteger(s, code, true);
                    if (arg2_val != null && arg2_val == pattern.integerConstant) return true;
                }
            }
        }

        if (pattern.type != arg2.type) return false;
        if (pattern.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
            return pattern.registerOrFlagName.equals(arg2.registerOrFlagName);
        }
        if (pattern.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            return pattern.integerConstant == arg2.integerConstant;
        }
        if (pattern.type == Expression.EXPRESSION_STRING_CONSTANT) {
            return pattern.stringConstant.equals(arg2.stringConstant);
        }
        if (pattern.type == Expression.EXPRESSION_SYMBOL) {
            return pattern.symbolName.equals(arg2.symbolName);
        }
        if (pattern.args != null && arg2.args != null && pattern.args.size() == arg2.args.size()) {
            // Special case of index registers (ix/iy):
            if (pattern.type == Expression.EXPRESSION_PARENTHESIS &&
                pattern.args.get(0).type == Expression.EXPRESSION_SUM &&
                pattern.args.get(0).args.get(0).type == Expression.EXPRESSION_SYMBOL &&
                pattern.args.get(0).args.get(0).symbolName.startsWith("?reg") &&
                pattern.args.get(0).args.get(1).type == Expression.EXPRESSION_SYMBOL &&
                pattern.args.get(0).args.get(1).symbolName.startsWith("?const")) {
                if (arg2.type == Expression.EXPRESSION_PARENTHESIS &&
                    arg2.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                    if (!unifyExpressions(pattern.args.get(0).args.get(0), arg2.args.get(0), false, match, s, code)) {
                        return false;
                    }
                    if (!match.addVariableMatch(pattern.args.get(0).args.get(1).symbolName, Expression.constantExpression(0, config))) {
                        return false;
                    }
                    return true;
                }
            }
            for(int i = 0;i<pattern.args.size();i++) {
                if (!unifyExpressions(pattern.args.get(i), arg2.args.get(i), false, match, s, code)) return false;
            }
            return true;
        }
        return false;
    }


    public boolean opMatch(CPUOpPattern pat1, CPUOp op2, CodeStatement s, CodeBase code, PatternMatch match)
    {
        if (pat1.args.size() != op2.args.size()) return false;
        if (pat1.opName.startsWith("?op")) {
            if (!match.addVariableMatch(pat1.opName, op2.spec.opNameExp)) {
                return false;
            }
        } else {
            if (!pat1.opName.equals(op2.spec.opName)) return false;
        }

        for(int i = 0;i<pat1.args.size();i++) {
            Expression arg1 = pat1.args.get(i);
            Expression arg2 = op2.args.get(i);                        
            if (!unifyExpressions(arg1, arg2, true, match, s, code)) return false;
        }

        // config.trace("opMatch: "+pat1+" with "+op2+" ("+match.variables+")");
        return true;
    }

    
    public List<String> applyBindingsToTokens(List<String> tokens, PatternMatch match)
    {
        // apply bindings:
        List<String> tokens2 = new ArrayList<>();
        for(int i = 0;i<tokens.size();i++) {
            if (tokens.get(i).equals("?") && match.variables.containsKey("?" + tokens.get(i+1))) {                            
                List<String> tokensTmp = config.tokenizer.tokenize(match.variables.get("?" + tokens.get(i+1)).toString());
                tokens2.addAll(tokensTmp);
                i++;    // we skip the second token we used
            } else {
                tokens2.add(tokens.get(i));
            }
        }
        return tokens2;
    }
    
    
    public void maybeLogOptimization(PatternMatch match, PatternBasedOptimizer pbo, SourceLine sl)
    {
        if (pbo.logPotentialOptimizations) {
            if (pbo.onlyOnePotentialOptimizationPerLine && pbo.alreadyShownAPotentialOptimization) return;
            String name2 = getInstantiatedName(match);
            config.info("Potential optimization ("+name2+") in " + sl);
            pbo.alreadyShownAPotentialOptimization = true;
        }
    }
    

    public PatternMatch match(int a_index, SourceFile f, CodeBase code,
                              PatternBasedOptimizer pbo)
    {
        int index = a_index;
        int index_to_display_message_on = -1;
        List<CodeStatement> l = f.getStatements();
        if (l.get(index).type != CodeStatement.STATEMENT_CPUOP) return null;
        PatternMatch match = new PatternMatch(this, f);
        
        // Match the CPU ops:
        for(int i = 0;i<pattern.size();i++) {
            CPUOpPattern patt = pattern.get(i);
            if (patt.isWildcard()) {
                if (i == pattern.size() - 1) {
                    // wildcard cannot be the last thing in a pattern!
                    return null;
                }
                CPUOpPattern nextPatt = pattern.get(i+1);
                List<CodeStatement> wildcardMatches = new ArrayList<>();

                while(true) {
                    if (index >= l.size()) return null;
                    CodeStatement s = l.get(index);
                    if (i!=0 && s.label != null) return null;
                    if (code.protectedFromOptimization(s)) return null;
//                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == CodeStatement.STATEMENT_CPUOP) {
                        PatternMatch matchTmp = new PatternMatch(match);
                        if (opMatch(nextPatt, s.op, s, code, matchTmp)) {
                            // we are done!
                            if (patt.ID == 0) index_to_display_message_on = index;
                            break;
                        } else {
                            // make sure it's not statement involving jumps (ret/call/jp/jr/djnz/reti/retn/...):
                            if (s.op.mightJump()) {
                                return null;
                            }
                            wildcardMatches.add(s);
                        }
                    } else if (!s.isEmptyAllowingComments()) {
                        return null;
                    }
                    index++;
                }

                match.map.put(patt.ID, wildcardMatches);
            } else if (patt.repetitionVariable != null) {
                // it's a potentially repeated line:
                List<CodeStatement> statementsMatched = new ArrayList<>();
                int count = 0;
                while(true) {
                    if (index >= l.size()) return null;
                    CodeStatement s = l.get(index);
                    if (i!=0 && s.label != null) return null;
                    if (i==0 && s.label != null && count>0) break;
                    if (code.protectedFromOptimization(s)) return null;
//                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == CodeStatement.STATEMENT_CPUOP) {
                        if (opMatch(patt, l.get(index).op, l.get(index), code, match)) {
                            count += 1;
                        } else {
                            if (count == 0) return null;
                            break;
                        }
                    } else if (!s.isEmptyAllowingComments()) {
                        return null;
                    }
                    if (count > 0) {
                        // matching started!
                        statementsMatched.add(l.get(index));
                    }
                    index++;
                }
                                
                // add matching to count:
                if (!match.addVariableMatch(patt.repetitionVariable, Expression.constantExpression(count, config))) {
                    return null;
                }
                match.map.put(patt.ID, statementsMatched);
                index++;
                
            } else {
                // not a wildcard, not a repetition:
                while(true) {
                    if (index >= l.size()) return null;
                    CodeStatement s = l.get(index);
                    if (i!=0 && s.label != null) return null;
                    if (code.protectedFromOptimization(s)) return null;
//                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == CodeStatement.STATEMENT_CPUOP) break;
                    if (!s.isEmptyAllowingComments()) return null;
                    index++;
                }
                if (!opMatch(patt, l.get(index).op, l.get(index), code, match)) return null;
                List<CodeStatement> tmp = new ArrayList<>();
                tmp.add(l.get(index));
                match.map.put(patt.ID, tmp);
                index++;
            }
            
            // check if we need to check any constraints at this point:
            for(Constraint constraint:constraints) {
                if (constraint.triggerAfterID == patt.ID) {
                    if (!checkConstraint(constraint, match, f, code, pbo,
                                         index_to_display_message_on)) return null;
                }
            }
            
        }
        
        if (index_to_display_message_on == -1) index_to_display_message_on = a_index;

        config.debug("Potential pattern match: " + this.name);
        
        // potential match! check constraints:
        for(Constraint constraint:constraints) {
            if (!checkConstraint(constraint, match, f, code, pbo,
                                 index_to_display_message_on)) {
                config.debug("  pattern did not match due to a constraint check: " + constraint);
                return null;
            }
        }
        
        // Some optimizations are not safe in some dialects. For example, SDCC
        // uses the stack in a very particular way, which makes MDL believe
        // some instructions are useless. Since, it is not always possible to
        // insert "mdl:-no-opt" tags in SDCC, we add a dialect-specific check
        // here:
        // Notice we use "pbo.config", since patterns use the default MDL
        // dialect, so, we need to access the pbo config to get the actual 
        // dialect we are currently using:
        if (pbo.config.dialectParser != null &&
            !pbo.config.dialectParser.safeOptimization(match)) {
            config.debug("  pattern did not match as the dialect reported it to be unsafe");
            return null;
        }

        return match;
    }
    
    
    public boolean regpairConstraint(String args[], String expected[])
    {
        if (!args[0].startsWith("?")) {
            // we need to construct the value from the second part:
            if (args[0].equalsIgnoreCase("bc")) {
                expected[0] = "bc"; expected[1] = "b"; expected[2] = "c";
                return true;
            }
            if (args[0].equalsIgnoreCase("de")) {
                expected[0] = "de"; expected[1] = "d"; expected[2] = "e";
                return true;
            }
            if (args[0].equalsIgnoreCase("hl")) {
                expected[0] = "hl"; expected[1] = "h"; expected[2] = "l";
                return true;
            }
            if (args[0].equalsIgnoreCase("ix")) {
                expected[0] = "ix"; expected[1] = "ixh"; expected[2] = "ixl";
                return true;
            }
            if (args[0].equalsIgnoreCase("iy")) {
                expected[0] = "iy"; expected[1] = "iyh"; expected[2] = "iyl";
                return true;
            }
        }
        if (!args[1].startsWith("?")) {
            // we need to construct the value from the second part:
            if (args[1].equalsIgnoreCase("b")) {
                expected[0] = "bc"; expected[1] = "b"; expected[2] = "c";
                return true;
            }
            if (args[1].equalsIgnoreCase("d")) {
                expected[0] = "de"; expected[1] = "d"; expected[2] = "e";
                return true;
            }
            if (args[1].equalsIgnoreCase("h")) {
                expected[0] = "hl"; expected[1] = "h"; expected[2] = "l";
                return true;
            }
            if (args[1].equalsIgnoreCase("ixh")) {
                expected[0] = "ix"; expected[1] = "ixh"; expected[2] = "ixl";
                return true;
            }
            if (args[1].equalsIgnoreCase("iyh")) {
                expected[0] = "iy"; expected[1] = "iyh"; expected[2] = "iyl";
                return true;
            }
        }
        if (!args[2].startsWith("?")) {
            // we need to construct the value from the second part:
            if (args[2].equalsIgnoreCase("c")) {
                expected[0] = "bc"; expected[1] = "b"; expected[2] = "c";
                return true;
            }
            if (args[2].equalsIgnoreCase("e")) {
                expected[0] = "de"; expected[1] = "d"; expected[2] = "e";
                return true;
            }
            if (args[2].equalsIgnoreCase("l")) {
                expected[0] = "hl"; expected[1] = "h"; expected[2] = "l";
                return true;
            }
            if (args[2].equalsIgnoreCase("ixl")) {
                expected[0] = "ix"; expected[1] = "ixh"; expected[2] = "ixl";
                return true;
            }
            if (args[2].equalsIgnoreCase("iyl")) {
                expected[0] = "iy"; expected[1] = "iyh"; expected[2] = "iyl";
                return true;
            }
        }
        return false;
    }

    
    public boolean checkConstraint(Constraint raw_constraint, PatternMatch match,
                                   SourceFile f, CodeBase code, PatternBasedOptimizer pbo,
                                   int index_to_display_message_on) {
        Constraint constraint = new Constraint(raw_constraint.name, 
                new String[raw_constraint.args.length], raw_constraint.triggerAfterID);
        for(int i = 0;i<raw_constraint.args.length;i++) {
            if (match.variables.containsKey(raw_constraint.args[i])) {
                constraint.args[i] = match.variables.get(raw_constraint.args[i]).toString();
            } else {
                constraint.args[i] = raw_constraint.args[i];
            }
        }

        switch(constraint.name) {
            case "regsNotUsedAfter":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                if (!match.map.containsKey(idx)) return false;
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    Boolean result = regNotUsedAfter(match.map.get(idx).get(match.map.get(idx).size()-1), reg, f, code);
                    if (result == null) {
                        maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                        return false;
                    } else {
                        if (!result) return false;
                    }
                }
                break;
            }
            case "flagsNotUsedAfter":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                if (!match.map.containsKey(idx)) return false;
                for(int i = 1;i<constraint.args.length;i++) {
                    String flag = constraint.args[i].replace(" ", "");   // this is because the P/V flag, otherwise, it's generated as "P / V" and there is no match

                    Boolean result = flagNotUsedAfter(match.map.get(idx).get(match.map.get(idx).size()-1), flag, f, code);
                    if (result == null) {
                        maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                        return false;
                    } else {
                        if (!result) return false;
                    }
                }
                break;
            }
            case "equal":
            {                    
                String v1_str = constraint.args[0];
                String v2_str = constraint.args[1];
                List<String> v1_tokens = applyBindingsToTokens(config.tokenizer.tokenize(v1_str), match);
                List<String> v2_tokens = applyBindingsToTokens(config.tokenizer.tokenize(v2_str), match);

                Expression exp1 = config.expressionParser.parse(v1_tokens, null, null, code);
                Expression exp2 = config.expressionParser.parse(v2_tokens, null, null, code);
                
                if (exp1 == null) {
                    config.error("Cannot parse " + v1_str + " as an expression");
                    return false;
                }
                if (exp2 == null) {
                    config.error("Cannot parse " + v2_str + " as an expression");
                    return false;
                }

                if (exp1.evaluatesToIntegerConstant() != exp2.evaluatesToIntegerConstant()) {
                    return false;
                }
                if (!config.labelsHaveSafeValues &&
                    (exp1.containsLabel(code) || exp2.containsLabel(code))) {
                    return false;
                }
                if (exp1.evaluatesToIntegerConstant()) {
                    // If the expressions are numeric, we evaluateToInteger them:
                    Integer v1 = exp1.evaluateToInteger(null, code, true);
                    Integer v2 = exp2.evaluateToInteger(null, code, true);
                    if (v1 == null || v2 == null) {
                        return false;
                    }
                    if ((int)v1 != (int)v2) {
                        return false;
                    }

                    if (exp1.containsSymbol() || exp2.containsSymbol()) {
                        match.newEqualities.add(new EqualityConstraint(exp1, null, exp2, null, false));
                    }
                } else {
                    // If they are not, then there is no need to evaluateToInteger, as they should just string match:
                    if (!v1_str.equalsIgnoreCase(v2_str)) {
                        return false;
                    }
                }
                break;
            }
            case "notEqual":
            {
                String v1_str = constraint.args[0];
                String v2_str = constraint.args[1];
                List<String> v1_tokens = applyBindingsToTokens(config.tokenizer.tokenize(v1_str), match);
                List<String> v2_tokens = applyBindingsToTokens(config.tokenizer.tokenize(v2_str), match);

                Expression exp1 = config.expressionParser.parse(v1_tokens, null, null, code);
                Expression exp2 = config.expressionParser.parse(v2_tokens, null, null, code);

                if (exp1.evaluatesToIntegerConstant() != exp2.evaluatesToIntegerConstant()) break;
                
                if (!config.labelsHaveSafeValues &&
                    (exp1.containsLabel(code) || exp2.containsLabel(code))) {
                    return false;
                }
                
                if (exp1.evaluatesToIntegerConstant()) {
                    // If the expressions are numeric, we evaluateToInteger them:
                    Integer v1 = exp1.evaluateToInteger(null, code, true);
                    Integer v2 = exp2.evaluateToInteger(null, code, true);
                    if (v1 == null || v2 == null) {
                        return false;
                    }
                    if (exp1.evaluateToInteger(null, code, true).equals(exp2.evaluateToInteger(null, code, true))) {
                        return false;
                    }

                    match.newEqualities.add(new EqualityConstraint(exp1, null, exp2, null, true));
                } else {
                    // If they are not, then there is no need to evaluateToInteger, as they should just string match:
                    if (v1_str.equalsIgnoreCase(v2_str)) {
                        return false;
                    }
                }
                break;
            }                
            case "in":
            {
                boolean found = false;
                for(int i = 1;i<constraint.args.length;i++) {
                    if (constraint.args[0].equalsIgnoreCase(constraint.args[i])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
                break;
            }
            case "notIn":
            {
                boolean found = false;
                for(int i = 1;i<constraint.args.length;i++) {
                    if (constraint.args[0].equalsIgnoreCase(constraint.args[i])) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    return false;
                }
                break;
            }
            case "regpair":
            {
                String expected[] = {null, null, null};
                if (!regpairConstraint(constraint.args, expected)) return false;
                if (constraint.args[0].startsWith("?")) {
                    if (!match.addVariableMatch(constraint.args[0], Expression.symbolExpression(expected[0], null, code, config))) {
                        return false;
                    }
                } else {
                    if (!constraint.args[0].equalsIgnoreCase(expected[0])) {
                        return false;
                    }
                }
                if (constraint.args[1].startsWith("?")) {
                    if (!match.addVariableMatch(constraint.args[1], Expression.symbolExpression(expected[1], null, code, config))) {
                        return false;
                    }
                } else {
                    if (!constraint.args[1].equalsIgnoreCase(expected[1])) {
                        return false;
                    }
                }
                if (constraint.args[2].startsWith("?")) {
                    if (!match.addVariableMatch(constraint.args[2], Expression.symbolExpression(expected[2], null, code, config))) {
                        return false;
                    }
                } else {
                    if (!constraint.args[2].equalsIgnoreCase(expected[2])) {
                        return false;
                    }
                }
                break;
            }
            case "reachableByJr":
            {
                CodeStatement start = match.map.get(Integer.parseInt(constraint.args[0])).get(0);
                Integer startAddress = start.getAddress(code);
                if (startAddress == null) {
                    return false;
                }
                startAddress += start.sizeInBytes(code, true, true, true);
                SourceConstant sc = code.getSymbol(constraint.args[1]);
                if (sc == null) {
                    return false;
                }
                Object tmp = sc.getValue(code, false);
                if (tmp == null && tmp instanceof Integer) {
                    return false;
                }
                Integer endAddress = (Integer)tmp;
                int diff = endAddress - startAddress;
                if (!CPUOp.offsetWithinJrRange(diff)) return false;
                break;
            }
            
            case "regsNotModified":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return true;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    for(CodeStatement s:statements) {
                        if (!regNotModified(s, reg, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("regsNotModified " + reg + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            
            case "regsModified":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    for(CodeStatement s:statements) {
                        if (!regNotModified(s, reg, f, code)) {
                            return true;
                        }
                    }
                }
                return false;
            }            
            
            case "flagsNotModified":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String flag = constraint.args[i];
                    for(CodeStatement s:statements) {
                        if (!flagNotModified(s, flag, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("flagsNotModified " + flag + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "regsNotUsed":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    for(CodeStatement s:statements) {
                        if (!regNotUsed(s, reg, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("regsNotModified " + reg + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "flagsNotUsed":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String flag = constraint.args[i];
                    for(CodeStatement s:statements) {
                        if (!flagNotUsed(s, flag, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("flagsNotUsed " + flag + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "evenPushPopsSPNotRead":
            {
                // Checks that:
                // - there is the same number of pushes than pops.
                // - SP is not explicitly read, like "add hl,sp".
                // - If SP had been assigned to IX/IY before, that memory
                //   pointed to by IX/IY is not read/written.
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                int stackMovements = 0;
                for(CodeStatement s:statements) {
                    if (s.type == CodeStatement.STATEMENT_CPUOP) {
                        if (s.op.spec.opName.equalsIgnoreCase("push")) {
                            stackMovements -= 2;
                        } else if (s.op.spec.opName.equalsIgnoreCase("pop")) {
                            stackMovements += 2;
                        } else if (s.op.spec.opName.equalsIgnoreCase("inc") &&
                                   s.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                                   s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
                            stackMovements ++;
                        } else if (s.op.spec.opName.equalsIgnoreCase("dec") &&
                                   s.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                                   s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
                            stackMovements --;
                        } else if (!s.op.args.isEmpty()) {
                            // check if the first operand is SP in any form:
                            Expression arg = s.op.args.get(0);
                            if (arg.type == Expression.EXPRESSION_REGISTER_OR_FLAG && 
                                arg.registerOrFlagName.equalsIgnoreCase("sp")) {
                                maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                                return false;
                            }
                            if (arg.type == Expression.EXPRESSION_PARENTHESIS && 
                                arg.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                                arg.args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
                                maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                                return false;
                            }
                        }
                        if ((s.op.spec.opName.equalsIgnoreCase("add") ||
                             s.op.spec.opName.equalsIgnoreCase("adc") ||
                             s.op.spec.opName.equalsIgnoreCase("sbc")) &&
                            s.op.args.size() == 2 &&
                            s.op.args.get(1).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                            s.op.args.get(1).registerOrFlagName.equalsIgnoreCase("sp")) {
                            maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                            return false;
                        }

                        if (pbo.statementsWhereIXIsSP == null) {
                            pbo.searchStatementsWhereIXIYAreSP(code);
                        }
                        for(Expression arg:s.op.args) {
                            if (arg.isIXIndirection()) {
                                if (pbo.statementsWhereIXIsSP.contains(s)) {
                                    // the instruction accesses memory pointed to by IX,
                                    // and IX at this point is acting as SP
                                    maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                                    return false;
                                }
                            } else  if (arg.isIYIndirection()) {
                                if (pbo.statementsWhereIYIsSP.contains(s)) {
                                    // the instruction accesses memory pointed to by IX,
                                    // and IX at this point is acting as SP
                                    maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                                    return false;
                                }
                            }
                        }
                        
                    }
                    // if at any point, there are more pops than push, stop:
                    if (stackMovements > 0) return false;
                }
                if (stackMovements != 0) return false;                
                break;
            }

            case "atLeastOneCPUOp":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                boolean found = false;
                for(CodeStatement s:statements) {
                    if (s.type == CodeStatement.STATEMENT_CPUOP) {
                        found = true;
                        break;
                    }
                }                    
                if (!found) return false;
                break;
            }
            
            case "noStackArguments":
            {
                String functionName = constraint.args[0];
                SourceConstant sc = code.getSymbol(functionName);
                if (sc == null) break;
                CodeStatement s = sc.definingStatement;
                if (s == null) break;
                // Look for a signs that would indicate if the function takes arguments in the stack:
                // - detect if SP is moved to IX or IY, either by "ld IX/IY, SP" or "add IX/IY, SP"
                // - we only check the first 10 ops of the function (or until we find a jump or a ret):
                int n_ops_left = 10;
                while(s != null && n_ops_left > 0) {
                    if (s.op != null) {
                        n_ops_left --;
                        
                        if (s.op.spec.opName.equalsIgnoreCase("ld") ||
                            s.op.spec.opName.equalsIgnoreCase("add")) {
                            if (s.op.args.get(0).isRegister() && 
                                s.op.args.get(1).isRegister() &&
                                (s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("ix") ||
                                 s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("iy")) &&
                                s.op.args.get(1).registerOrFlagName.equalsIgnoreCase("sp")) {
                                // function is likely to accept stack parameters!
                                return false;
                            }
                        }
                    }
                    s = s.source.getNextStatementTo(s, code);
                }
                break;
            }
            
            case "memoryNotWritten":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return true;
                }
                String expression = constraint.args[1];
                for(String variable: match.variables.keySet()) {
                    expression = expression.replace(variable, match.variables.get(variable).toString());
                }
                List<String> tokens = config.tokenizer.tokenize(expression);
                // We add a parenthesis, since that's how it is in the ops:
                Expression exp = Expression.parenthesisExpression(
                        config.expressionParser.parse(tokens, null, null, code),
                        "(", config);
                for(CodeStatement s:statements) {
                    if (s.op == null) continue;
                    if (s.op.isLdToMemory()) {
                        PatternMatch match2 = new PatternMatch(match);
                        if (unifyExpressions(exp, s.op.args.get(0), true, match2, s, code)) {
                            return false;
                        }
                    }
                }
                break;
            }
            
            case "memoryNotUsed":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<CodeStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return true;
                }
                String expression = constraint.args[1];
                for(String variable: match.variables.keySet()) {
                    expression = expression.replace(variable, match.variables.get(variable).toString());
                }
                List<String> tokens = config.tokenizer.tokenize(expression);
                // We add a parenthesis, since that's how it is in the ops:
                Expression exp = Expression.parenthesisExpression(
                        config.expressionParser.parse(tokens, null, null, code),
                        "(", config);
                for(CodeStatement s:statements) {
                    if (s.op == null) continue;
                    int i = s.op.spec.args.size() - 1;  // check only the last argument (for reads)
                    CPUOpSpecArg argSpec = s.op.spec.args.get(i);
                    if (argSpec.wordConstantIndirectionAllowed ||
                        argSpec.regIndirection != null ||
                        argSpec.regOffsetIndirection != null) {
                        PatternMatch match2 = new PatternMatch(match);
                        if (unifyExpressions(exp, s.op.args.get(i), true, match2, s, code)) {
                            return false;
                        }
                    }
                }
                break;
            }            
            
            default:
                throw new UnsupportedOperationException("Unknown pattern constraint " + constraint.name);
        }        
        return true;
    }
    

    public boolean apply(SourceFile f, PatternMatch match, 
                         CodeBase code,
                         List<EqualityConstraint> equalitiesToMaintain)
    {
        // undo record:
        List<Pair<Integer, CodeStatement>> undo = new ArrayList<>();
        
        List<CodeStatement> l = f.getStatements();
        List<Integer> replacementIDs = new ArrayList<>();
        int insertionPoint = -1;
        CPUOpPattern lastReplacementInserted = null;
        HashMap<CPUOpPattern, Integer> replacementInsertionPoints = new HashMap<>();
        CodeStatement lastRemoved = null;
                
        for(CPUOpPattern p:replacement) {
            replacementIDs.add(p.ID);
        }
        for(int i = 0;i<pattern.size();i++) {
            int key = pattern.get(i).ID;
            if (pattern.get(i).isWildcard()) {
                // It's a wildcard:
                boolean found = false;
                for(int j = 0;j<replacement.size();j++) {
                    if (replacement.get(j).ID == pattern.get(i).ID) {
                        replacementIDs.remove((Integer)replacement.get(j).ID);
                        if (!replacement.get(j).isWildcard()) {
                            config.error("Replacing instructions matched with a wildcard is not yet supported!");
                            return false;
                        } else {
                            List<CodeStatement> l2 = match.map.get(key);
                            if (!l2.isEmpty()) {
                                replacementInsertionPoints.put(replacement.get(j), l.indexOf(l2.get(0)));
                            }
                            found = true;
                            insertionPoint = -1;
                            lastReplacementInserted = replacement.get(j);
                            break;
                        }
                    }
                }
                if (!found) {
                    config.error("Removing instructions matched with a wildcard is not yet supported!");
                    return false;
                }                
            } else {
                // It is a regular op (not a wildcard):
                CodeStatement removedLabel = null;
                for(CodeStatement s:match.map.get(key)) {
                    insertionPoint = l.indexOf(s);
                    lastRemoved = l.remove(insertionPoint);
                    match.removed.add(lastRemoved);
                    if (lastRemoved.label != null) {
                        if (removedLabel != null) {
                            config.error("There were more than one label in the matched instructions of a pattern, which should not have happened!");
                            return false;
                        } else {
                            removedLabel = lastRemoved;
                        }
                    }
                    undo.add(Pair.of(insertionPoint, lastRemoved));
                }
                if (lastRemoved == null) {
                    config.error("optimization pattern line matched with one of its lines mathcing to zero ops. This should not have happened!");
                    return false;
                }
                boolean replaced = false;
                for(int j = 0;j<replacement.size();j++) {
                    if (replacement.get(j).ID == pattern.get(i).ID) {
                        replacementIDs.remove((Integer)replacement.get(j).ID);
                        CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_CPUOP, lastRemoved.sl, lastRemoved.source, config);
                        // if the original statement had a label, we need to keep it!
                        if (removedLabel != null) {
                            s.label = removedLabel.label;
                            s.label.definingStatement = s;
                            removedLabel = null;
                        }
                        s.op = new CPUOp(replacement.get(j).instantiate(match, this, config));
                        if (s.op == null) {
                            config.error("Problem applying optimization to replace: " + lastRemoved);
                            config.error("The replacement was: " + replacement.get(j));
                            return false;
                        }
                        if (replacement.get(j).repetitionVariable == null) {
                            replacementInsertionPoints.put(replacement.get(j), insertionPoint);
                            l.add(insertionPoint, s);
                            match.added.add(s);
                            insertionPoint++;
                            replaced = true;
                            lastReplacementInserted = replacement.get(j);
                            undo.add(Pair.of(null, s));
                            break;
                        } else {
                            // We need to insert "s" more than one time:
                            Integer repetitions = match.variables.get(replacement.get(j).repetitionVariable).evaluateToInteger(null, code, true);
                            if (repetitions == null) {
                                config.error("Problem applying optimization pattern, could not evaluate " + replacement.get(j).repetitionVariable + " to an integer!");
                                return false;
                            }
                            for(int k = 0;k<repetitions;k++) {
                                replacementInsertionPoints.put(replacement.get(j), insertionPoint);
                                l.add(insertionPoint, s);
                                match.added.add(s);
                                insertionPoint++;
                                replaced = true;
                                lastReplacementInserted = replacement.get(j);
                                undo.add(Pair.of(null, s));
                                
                                // new statement for the next iteration (notice we will create one too many, but it's fine):
                                s = new CodeStatement(CodeStatement.STATEMENT_CPUOP, lastRemoved.sl, lastRemoved.source, config);
                                s.op = new CPUOp(replacement.get(j).instantiate(match, this, config));                    
                            }
                            break;
                        }
                    }
                }
                if (!replaced && removedLabel != null) {
                    // We were losing a label. Insert a dummy statement with the label we lost:
                    CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_NONE, removedLabel.sl, removedLabel.source, config);
                    s.label = removedLabel.label;
                    s.label.definingStatement = s;
                    l.add(insertionPoint, s);
                    match.added.add(s);
                    insertionPoint++;
                    undo.add(Pair.of(null, s));
                }                
            }
        }
        
        // add the missing replacements:
        for(int ID:replacementIDs) {
            CPUOpPattern r = null;
            int rInsertionPoint;
            int replacementIdx = -1;
            for(int i = 0;i<replacement.size();i++) {
                CPUOpPattern r2 = replacement.get(i);
                if (r2.ID == ID) {
                    r = r2;
                    replacementIdx = i;
                    break;
                }
            }
            if (r == null) {
                config.error("Could not find replacement pattern with ID " + ID);
                return false;
            }
            
            config.debug("inserting additional replacement " + ID + " (idx: " + replacementIdx + ")");
            config.debug("IDs with insertion points: " + replacementInsertionPoints.keySet());
            
            // find the insertion point:
            if (replacementIdx == replacement.size() - 1) {
                rInsertionPoint = insertionPoint;
            } else if (replacementInsertionPoints.containsKey(replacement.get(replacementIdx+1))) {
                rInsertionPoint = replacementInsertionPoints.get(replacement.get(replacementIdx+1));
            } else if (replacementIdx > 0 && lastReplacementInserted == replacement.get(replacementIdx-1)) {
                rInsertionPoint = insertionPoint;
            } else if (replacementIdx > 0 && replacementInsertionPoints.containsKey(replacement.get(replacementIdx-1))) {
                CPUOpPattern p = replacement.get(replacementIdx-1);
                if (!p.isWildcard()) {
                    rInsertionPoint = replacementInsertionPoints.get(p) + 1;
                } else {
                    config.error("Unsupported case when inserting new replacements in an optimization pattern! (name: " + name + ") " + message);
                    return false;
                }
            } else {
                config.error("Unsupported case when inserting new replacements in an optimization pattern! (name: " + name + ") " + message);
                return false;
            }
                        
            if (rInsertionPoint == -1) {
                config.error("Could not determine the insertion point in an additional replacement for: " + r);
                return false;
            }
            if (lastRemoved == null) {
                config.error("Could not determine the source for an additional replacement for: " + r);
                return false;
            }
            CodeStatement s = new CodeStatement(CodeStatement.STATEMENT_CPUOP, lastRemoved.sl, lastRemoved.source, config);
            s.op = new CPUOp(r.instantiate(match, this, config));
            if (s.op == null) {
                config.error("The replacement was: " + r);
                return false;
            }
            l.add(rInsertionPoint, s);
            match.added.add(s);
            lastReplacementInserted = r;
            undo.add(Pair.of(null, s));
            // Shift all the necessary insertion points one up:
            for(CPUOpPattern p:replacementInsertionPoints.keySet()) {
                int oldInsertionPoint = replacementInsertionPoints.get(p);
                if (oldInsertionPoint >= rInsertionPoint) {
                    replacementInsertionPoints.put(p, oldInsertionPoint + 1);
                }
            }
            replacementInsertionPoints.put(r, rInsertionPoint);
            rInsertionPoint++;
            insertionPoint = rInsertionPoint;
        }
        
        // Add new equality constraints (we add them first, in case the optimization itself breaks them):
        int previousLength = equalitiesToMaintain.size();
        equalitiesToMaintain.addAll(match.newEqualities);
        
        code.resetAddresses();
        
        // Check the equalities:
        // config.debug("Checking " + equalitiesToMaintain.size() + " equalities!");
        boolean undoOptimization = false;
        EqualityConstraint brokenEquality = null;
        for(EqualityConstraint eq:equalitiesToMaintain) {
            if (!eq.check(code, config)) {    
                undoOptimization = true;
                brokenEquality = eq;
                break;
            }
        }
        
        // If the pattern increased the size of the program:
        if (!undoOptimization && getSpaceSaving(match, code) < 0) {
            // Check all relative jumps are still within reach:
            code.resetAddresses();
            if (code.checkRelativeJumpsInRange() != null) {
                undoOptimization = true;
            }                
        }
        
        if (undoOptimization) {
            // undo the optimization:
            for(int i = undo.size()-1; i>=0; i--) {
                if (undo.get(i).getLeft() == null) {
                    // remove:
                    l.remove(undo.get(i).getRight());
                } else {
                    // add:
                    l.add(undo.get(i).getLeft(), undo.get(i).getRight());
                }
            }
            while(equalitiesToMaintain.size() > previousLength) equalitiesToMaintain.remove(equalitiesToMaintain.size()-1);
            if (brokenEquality != null) {
                config.debug("Optimization undone, as it was breaking the equality constraint: " + brokenEquality.exp1 + " == " + brokenEquality.exp2);
            } else {
                config.debug("Optimization undone, as it was breaking a relative jump.");
            }
            code.resetAddresses();
            return false;            
        }
                
        return true;
    }


    public static boolean regNotModified(CodeStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);        
        if (s.type == CodeStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            CPUOpDependency dep2 = op.checkOutputDependency(dep);
            return dep.equals(dep2);
        } else {
            return true;
        }
    }
    
    
    public boolean flagNotModified(CodeStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);        
        if (s.type == CodeStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            CPUOpDependency dep2 = op.checkOutputDependency(dep);
            return dep.equals(dep2);
        } else {
            return true;
        }
    }    
    

    public static boolean regNotUsed(CodeStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);        
        if (s.type == CodeStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            return !op.checkInputDependency(dep);
        } else {
            return true;
        }
    }
    
    
    public boolean flagNotUsed(CodeStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);        
        if (s.type == CodeStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            return !op.checkInputDependency(dep);
        } else {
            return true;
        }
    }    
    
    
    public static Boolean regNotUsedAfter(CodeStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);
        return depNotUsedAfter(s, dep, f, code);
    }


    public static Boolean flagNotUsedAfter(CodeStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);
        return depNotUsedAfter(s, dep, f, code);
    }


    // - returns true/false if we know for sure the dependency is or not used
    // - returns null when it's unclear
    public static Boolean depNotUsedAfter(CodeStatement s, CPUOpDependency a_dep, SourceFile f, CodeBase code)
    {
        List<DepCheckNode> open = new ArrayList<>();
        HashMap<CodeStatement,List<DepCheckNode>> closed = new HashMap<>();
        List<Pair<CodeStatement, List<CodeStatement>>> tmp = f.nextExecutionStatements(s, true, new ArrayList<>(), code);
        if (tmp == null) {
            // It's hard to tell where is this instruction going to jump,
            // so we act conservatively, and block the optimization:
            // config.trace("    unclear next statement after " + s);
            return false;
        }
        for(Pair<CodeStatement, List<CodeStatement>> pair:tmp) {
            DepCheckNode node = new DepCheckNode(pair.getLeft(), a_dep, pair.getRight());
            open.add(node);
            List<DepCheckNode> l = new ArrayList<>();
            l.add(node);
            closed.put(pair.getLeft(), l);
        }
        while(!open.isEmpty()) {
            DepCheckNode node = open.remove(0);
            CodeStatement next = node.s;
            CPUOpDependency dep = node.dep;
            List<CodeStatement> callStack = node.callStack;
            // config.trace("    "+next.sl.lineNumber+": "+next);

            if (next.type == CodeStatement.STATEMENT_CPUOP) {
                CPUOp op = next.op;
//                if (op.isRet()) {
//                    // It's hard to tell where is this instruction going to jump,
//                    // so we act conservatively, and block the optimization:
//                    // config.trace("    ret!");
//                    return null;
//                }
                if (op.checkInputDependency(dep)) {
                    // dependency is actually used!
                    // config.trace("    dependency found!");
                    return false;
                }
                dep = op.checkOutputDependency(dep);
//                if (dep == null) {
//                    // config.trace("    dependency broken!");
//                }
            } else if (next.type == CodeStatement.STATEMENT_DATA_BYTES ||
                       next.type == CodeStatement.STATEMENT_DATA_WORDS ||
                       next.type == CodeStatement.STATEMENT_DATA_DOUBLE_WORDS) {
                // There is either a bug in the program, or some assembler instructions
                // are coded directly in data statements, assume dependency for safety:
                return false;
            }
            
            if (dep != null) {
                // add successors:
                List<Pair<CodeStatement, List<CodeStatement>>> nextNext_l = next.source.nextExecutionStatements(next, true, callStack, code);
                if (nextNext_l == null) {
                    // It's hard to tell where is this instruction going to jump,
                    // so we act conservatively, and block the optimization:
                    // config.trace("    unclear next statement after: "+next);
                    return null;
                }
                for(Pair<CodeStatement, List<CodeStatement>> nextNext_pair: nextNext_l) {
                    CodeStatement nextNext = nextNext_pair.getLeft();
                    List<CodeStatement> nextNext_stack = nextNext_pair.getRight();
                    if (nextNext_stack != null && !nextNext_stack.isEmpty()) {
                        int size = nextNext_stack.size();
                        if (nextNext_stack.indexOf(nextNext_stack.get(size-1)) != size-1) {
                            // recursive call, we will get into an infinite loop, so, stop it!
                            return null;
                        }
                    }
                    if (!closed.containsKey(nextNext)) {
                        DepCheckNode nextNode = new DepCheckNode(nextNext, dep, nextNext_stack);
                        open.add(nextNode);
                        List<DepCheckNode> l = new ArrayList<>();
                        l.add(nextNode);
                        closed.put(nextNext, l);
                    } else {
                        List<DepCheckNode> l = closed.get(nextNext);
                        boolean found = false;
                        for(DepCheckNode n:l) {
                            if (n.match(dep, nextNext_stack)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            DepCheckNode nextNode = new DepCheckNode(nextNext, dep, nextNext_stack);
                            l.add(nextNode);
                            open.add(nextNode);
                        }
                    }
                }
            }
        }
        
        return true;
    }
    
    
    // Checks:
    // - that there is no unbound variable on the replacement
    // - that all numbers appearing in the pattern, appear in the same order
    //   in the replacement.
    // - that there is at least one element in the pattern with ID == 0
    public boolean checkIntegrity(MDLConfig config)
    {
        CodeBase code = new CodeBase(config);
        List<String> definedSymbols = new ArrayList<>();
        List<String> replacementSymbols = new ArrayList<>();
        for(CPUOpPattern p:pattern) {
            for(Expression exp:p.args) {
                definedSymbols.addAll(exp.getAllSymbols());
            }
        }
        for(Constraint c:constraints) {
            for(String arg:c.args) {
                List<String> tokens = config.tokenizer.tokenize(arg);
                Expression exp = config.expressionParser.parse(tokens, null,
                                                               null, code);
                definedSymbols.addAll(exp.getAllSymbols());
            }
        }
        for(CPUOpPattern p:replacement) {
            for(Expression exp:p.args) {
                replacementSymbols.addAll(exp.getAllSymbols());
            }
        }
        for(String symbol:replacementSymbols) {
            if (!definedSymbols.contains(symbol)) {
                config.error("Symbol " + symbol + " is unbound in pattern: " + name);
                return false;
            }
            
        }
        
        for(int i = 0;i<pattern.size();i++) {
            for(int j = i+1;j<pattern.size();j++) {
                int pattID1 = pattern.get(i).ID;
                int pattID2 = pattern.get(j).ID;
                Integer repIdx1 = null;
                Integer repIdx2 = null;
                for(int k = 0;k<replacement.size();k++) {
                    if (replacement.get(k).ID == pattID1) repIdx1 = k;
                    if (replacement.get(k).ID == pattID2) repIdx2 = k;
                }
                if (repIdx1 != null && repIdx2 != null && repIdx1 > repIdx2) {
                    config.error("Wrong ID order in pattern: " + name);
                    return false;
                }
            }
        }
        
        // make sure that at least one of the lines in "pattern" has id 0 (and is not a wildcard):
        boolean found = false;
        for(CPUOpPattern pat:pattern) {
            if (pat.ID == 0 && !pat.isWildcard()) {
                found = true;
            }
        }
        if (!found) {
            config.error("Pattern \""+message+"\" does not contain a non wildcard line with ID 0!");
            return false;
        }        
        
        return true;
    }
    
    
    public boolean hasPotentialEqualityConstraints()
    {
        for(Constraint c:constraints) {
            if (c.name.equals("equal") ||
                c.name.equals("notEqual")) {
                return true;
            }
        }
        return false;
    }
}

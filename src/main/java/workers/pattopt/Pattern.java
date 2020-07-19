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
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class Pattern {
    MDLConfig config;

    String name;
    List<CPUOpPattern> pattern = new ArrayList<>();
    List<CPUOpPattern> replacement = new ArrayList<>();
    List<String []> constraints = new ArrayList<>();

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
                name = line.substring(8).trim();
                state = 1;
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
                        }
                        break;
                    }

                    case 2: // replacement:
                    {
                        CPUOpPattern patt = CPUOpPattern.parse(line, patternCB, config);
                        if (patt != null) {
                            replacement.add(patt);
                        }
                        break;
                    }
                    case 3: // constraints:
                    {
                        List<String> tokens = Tokenizer.tokenize(line);
                        String name = tokens.remove(0);
                        List<Expression> expressions = new ArrayList<>();
                        if (!tokens.get(0).equals("(")) throw new RuntimeException("cannot parse constraint: " + line);
                        tokens.remove(0);
                        while(!tokens.get(0).equals(")")) {
                            Expression exp = config.expressionParser.parse(tokens, null, patternCB);
                            if (exp == null) throw new RuntimeException("cannot parse constraint: " + line);
                            expressions.add(exp);
                            if (tokens.get(0).equals(",")) tokens.remove(0);
                        }
                        
                        String split[] = new String[expressions.size()+1];
                        split[0] = name;
                        for(int i = 1;i<split.length;i++) {
                            split[i] = expressions.get(i-1).toString();
                        }
                        constraints.add(split);
                        break;
                    }
                    default:
                        throw new RuntimeException("Unexpected line parsing a pattern: " + line);
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
            config.error("Pattern \""+name+"\" does not contain a non wildcard line with ID 0!");
        }

        config.trace("parsed pattern: " + name);
    }


    public String getName()
    {
        return name;
    }
        
    
    public String getInstantiatedName(PatternMatch match)
    {
        String tmp = name;
        for(String variable:match.variables.keySet()) {
            tmp = tmp.replace(variable, match.variables.get(variable).toString());
        }
        return tmp;
    }


    public int getSpaceSaving(PatternMatch match)
    {
        int patternSize = 0;
        int replacementSize = 0;
        for(CPUOpPattern pat:pattern) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, config);
                patternSize += ipat.sizeInBytes();
            }
        }
        for(CPUOpPattern pat:replacement) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, config);
                replacementSize += ipat.sizeInBytes();
            }
        }
        int spaceSaving = patternSize - replacementSize;
        return spaceSaving;
    }
    
    
    public int[] getTimeSaving(PatternMatch match)
    {
        int patternTime[] = {0,0};
        int replacementTime[] = {0,0};
        for(CPUOpPattern pat:pattern) {
            if (!pat.isWildcard()) {
                int tmp[] = pat.instantiate(match, config).timing();
                patternTime[0] += tmp[0];
                if (tmp.length>1) {
                    patternTime[1] += tmp[1];
                } else {
                    patternTime[1] += tmp[0];
                }
            }
        }
        for(CPUOpPattern pat:replacement) {
            if (!pat.isWildcard()) {
                int tmp[] = pat.instantiate(match, config).timing();
                replacementTime[0] += tmp[0];
                if (tmp.length>1) {            
                    replacementTime[1] += tmp[1];
                } else {
                    replacementTime[1] += tmp[0];
                }
            }
        }
        return new int[]{patternTime[0] - replacementTime[0],
                         patternTime[1] - replacementTime[1]};
    }
    
    
    public String getTimeSavingString(PatternMatch match)
    {
        int tmp[] = getTimeSaving(match);
        if (tmp[0] == tmp[1]) {
            return ""+tmp[0];
        } else {
            return tmp[0] + "/" + tmp[1];
        }
    }
    
    
    public boolean unifyExpressions(Expression pattern, Expression arg2, boolean expressionRoot, PatternMatch match, SourceStatement s, CodeBase code)
    {
        if (pattern.type == Expression.EXPRESSION_SYMBOL &&
            pattern.symbolName.startsWith("?")) {
            // it's a variable!
            if (pattern.symbolName.startsWith("?reg")) {
                if (arg2.isRegister(code)) {
                    return match.addVariableMatch(pattern.symbolName, arg2);
                } else {
                    return false;
                }
            } else if (pattern.symbolName.startsWith("?const")) {
                // We expluce matches with "parenthesis" expressions, as those might be indirections
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
            for(int i = 0;i<pattern.args.size();i++) {
                if (!unifyExpressions(pattern.args.get(i), arg2.args.get(i), false, match, s, code)) return false;
            }
            return true;
        }
        return false;
    }


    public boolean opMatch(CPUOpPattern pat1, CPUOp op2, SourceStatement s, CodeBase code, PatternMatch match)
    {
        if (!pat1.opName.equals(op2.spec.opName)) return false;
        if (pat1.args.size() != op2.args.size()) return false;

        for(int i = 0;i<pat1.args.size();i++) {
            Expression arg1 = pat1.args.get(i);
            Expression arg2 = op2.args.get(i);                        
            if (!unifyExpressions(arg1, arg2, true, match, s, code)) return false;
        }

        config.trace("opMatch: "+pat1+" with "+op2+" ("+match.variables+")");
        return true;
    }

    
    public List<String> applyBindingsToTokens(List<String> tokens, PatternMatch match)
    {
        // apply bindings:
        List<String> tokens2 = new ArrayList<>();
        for(int i = 0;i<tokens.size();i++) {
            if (tokens.get(i).equals("?") && match.variables.containsKey("?" + tokens.get(i+1))) {                            
                List<String> tokensTmp = Tokenizer.tokenize(match.variables.get("?" + tokens.get(i+1)).toString());
                tokens2.addAll(tokensTmp);
                i++;    // we skip the second token we used
            } else {
                tokens2.add(tokens.get(i));
            }
        }
        return tokens2;
    }
    

    public PatternMatch match(int a_index, SourceFile f, CodeBase code, MDLConfig config,
                              boolean logPatternsMatchedWithViolatedConstraints)
    {
        int index = a_index;
        List<SourceStatement> l = f.getStatements();
        if (l.get(index).type != SourceStatement.STATEMENT_CPUOP) return null;
        PatternMatch match = new PatternMatch();
        
        // Match the CPU ops:
        for(int i = 0;i<pattern.size();i++) {
            CPUOpPattern patt = pattern.get(i);
            if (patt.isWildcard()) {
                if (i == pattern.size() - 1) {
                    // wildcard cannot be the last thing in a pattern!
                    return null;
                }
                CPUOpPattern nextPatt = pattern.get(i+1);
                List<SourceStatement> wildcardMatches = new ArrayList<>();

                while(true) {
                    if (index >= l.size()) return null;
                    SourceStatement s = l.get(index);
                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == SourceStatement.STATEMENT_CPUOP) {
                        PatternMatch matchTmp = new PatternMatch(match);
                        if (opMatch(nextPatt, s.op, s, code, matchTmp)) {
                            // we are done!
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

                match.wildCardMap.put(patt.ID, wildcardMatches);
            } else {
                while(true) {
                    if (index >= l.size()) return null;
                    SourceStatement s = l.get(index);
                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == SourceStatement.STATEMENT_CPUOP) break;
                    if (!s.isEmptyAllowingComments()) return null;
                    index++;
                }
                if (!opMatch(patt, l.get(index).op, l.get(index), code, match)) return null;
                match.opMap.put(patt.ID, l.get(index));
                index++;
            }
        }

        // potential match! check constraints:
        for(String[] raw_constraint:constraints) {
            String []constraint = new String[raw_constraint.length];
            for(int i = 0;i<raw_constraint.length;i++) {
                if (match.variables.containsKey(raw_constraint[i])) {
                    constraint[i] = match.variables.get(raw_constraint[i]).toString();
                } else {
                    constraint[i] = raw_constraint[i];
                }
            }

            switch(constraint[0]) {
                case "regsNotUsedAfter":
                {
                    int idx = Integer.parseInt(constraint[1]);
                    if (!match.opMap.containsKey(idx)) return null;
                    for(int i = 2;i<constraint.length;i++) {
                        String reg = constraint[i];
                        if (!regNotUsed(match.opMap.get(idx), reg, f, code)) {
                            if (logPatternsMatchedWithViolatedConstraints)
                                config.info("Potential optimization ("+name+") in " + f.getStatements().get(a_index).sl);
                            return null;
                        }
                    }
                    break;
                }
                case "flagsNotUsedAfter":
                {
                    int idx = Integer.parseInt(constraint[1]);
                    if (!match.opMap.containsKey(idx)) return null;
                    for(int i = 2;i<constraint.length;i++) {
                        String flag = constraint[i];

                        if (!flagNotUsed(match.opMap.get(idx), flag, f, code)) {
                            if (logPatternsMatchedWithViolatedConstraints)
                                config.info("Potential optimization ("+name+") in " + f.getStatements().get(a_index).sl);
                            return null;
                        }
                    }
                    break;
                }
                case "equal":
                {
                    String v1_str = constraint[1];
                    String v2_str = constraint[2];
                    List<String> v1_tokens = applyBindingsToTokens(Tokenizer.tokenize(v1_str), match);
                    List<String> v2_tokens = applyBindingsToTokens(Tokenizer.tokenize(v2_str), match);
                    
                    Expression exp1 = config.expressionParser.parse(v1_tokens, null, code);
                    Expression exp2 = config.expressionParser.parse(v2_tokens, null, code);
                    
                    if (exp1.evaluatesToIntegerConstant() != exp2.evaluatesToIntegerConstant()) return null;
                    if (exp1.evaluatesToIntegerConstant()) {
                        // If the expressions are numeric, we evaluateToInteger them:
                        Integer v1 = exp1.evaluateToInteger(null, code, true);
                        Integer v2 = exp2.evaluateToInteger(null, code, true);
                        if (v1 == null || v2 == null) return null;
                        if ((int)v1 != (int)v2) return null;
                    } else {
                        // If they are not, then there is no need to evaluateToInteger, as they should just string match:
                        if (!v1_str.equalsIgnoreCase(v2_str)) return null;
                    }
                    break;
                }
                case "notEqual":
                {
                    String v1_str = constraint[1];
                    String v2_str = constraint[2];
                    List<String> v1_tokens = applyBindingsToTokens(Tokenizer.tokenize(v1_str), match);
                    List<String> v2_tokens = applyBindingsToTokens(Tokenizer.tokenize(v2_str), match);

                    Expression exp1 = config.expressionParser.parse(v1_tokens, null, code);
                    Expression exp2 = config.expressionParser.parse(v2_tokens, null, code);

                    if (exp1.evaluatesToIntegerConstant() != exp2.evaluatesToIntegerConstant()) break;
                    if (exp1.evaluatesToIntegerConstant()) {
                        // If the expressions are numeric, we evaluateToInteger them:
                        Integer v1 = exp1.evaluateToInteger(null, code, true);
                        Integer v2 = exp2.evaluateToInteger(null, code, true);
                        if (v1 == null || v2 == null) return null;
                        if (exp1.evaluateToInteger(null, code, true).equals(exp2.evaluateToInteger(null, code, true))) return null;
                    } else {
                        // If they are not, then there is no need to evaluateToInteger, as they should just string match:
                        if (v1_str.equalsIgnoreCase(v2_str)) return null;
                    }
                    break;
                }                
                case "in":
                {
                    boolean found = false;
                    for(int i = 2;i<constraint.length;i++) {
                        if (constraint[1].equalsIgnoreCase(constraint[i])) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return null;
                    }
                    break;
                }
                case "regpair":
                {
                    String expected1 = null;
                    String expected2 = null;
                    String expected3 = null;
                    if (!constraint[1].startsWith("?")) {
                        // we need to construct the value from the second part:
                        if (constraint[1].equalsIgnoreCase("bc")) {
                            expected1 = "bc"; expected2 = "b"; expected3 = "c";
                        }
                        if (constraint[1].equalsIgnoreCase("de")) {
                            expected1 = "de"; expected2 = "d"; expected3 = "e";
                        }
                        if (constraint[1].equalsIgnoreCase("hl")) {
                            expected1 = "hl"; expected2 = "h"; expected3 = "l";
                        }
                        if (constraint[1].equalsIgnoreCase("ix")) {
                            expected1 = "ix"; expected2 = "ixh"; expected3 = "ixl";
                        }
                        if (constraint[1].equalsIgnoreCase("iy")) {
                            expected1 = "iy"; expected2 = "iyh"; expected3 = "iyl";
                        }
                    }
                    if (!constraint[2].startsWith("?")) {
                        // we need to construct the value from the second part:
                        if (constraint[2].equalsIgnoreCase("b")) {
                            expected1 = "bc"; expected2 = "b"; expected3 = "c";
                        }
                        if (constraint[2].equalsIgnoreCase("d")) {
                            expected1 = "de"; expected2 = "d"; expected3 = "e";
                        }
                        if (constraint[2].equalsIgnoreCase("h")) {
                            expected1 = "hl"; expected2 = "h"; expected3 = "l";
                        }
                        if (constraint[2].equalsIgnoreCase("ixh")) {
                            expected1 = "ix"; expected2 = "ixh"; expected3 = "ixl";
                        }
                        if (constraint[2].equalsIgnoreCase("iyh")) {
                            expected1 = "iy"; expected2 = "iyh"; expected3 = "iyl";
                        }
                    }
                    if (!constraint[3].startsWith("?")) {
                        // we need to construct the value from the second part:
                        if (constraint[3].equalsIgnoreCase("c")) {
                            expected1 = "bc"; expected2 = "b"; expected3 = "c";
                        }
                        if (constraint[3].equalsIgnoreCase("e")) {
                            expected1 = "de"; expected2 = "d"; expected3 = "e";
                        }
                        if (constraint[3].equalsIgnoreCase("l")) {
                            expected1 = "hl"; expected2 = "h"; expected3 = "l";
                        }
                        if (constraint[3].equalsIgnoreCase("ixl")) {
                            expected1 = "ix"; expected2 = "ixh"; expected3 = "ixl";
                        }
                        if (constraint[3].equalsIgnoreCase("iyl")) {
                            expected1 = "iy"; expected2 = "iyh"; expected3 = "iyl";
                        }
                    }
                    if (expected1 == null || expected2 == null || expected3 == null) return null;
                    if (constraint[1].startsWith("?")) {
                        if (!match.addVariableMatch(constraint[1], Expression.symbolExpression(expected1, code, config))) return null;
                    } else {
                        if (!constraint[1].equalsIgnoreCase(expected1)) return null;
                    }
                    if (constraint[2].startsWith("?")) {
                        if (!match.addVariableMatch(constraint[2], Expression.symbolExpression(expected2, code, config))) return null;
                    } else {
                        if (!constraint[2].equalsIgnoreCase(expected2)) return null;
                    }
                    if (constraint[3].startsWith("?")) {
                        if (!match.addVariableMatch(constraint[3], Expression.symbolExpression(expected3, code, config))) return null;
                    } else {
                        if (!constraint[3].equalsIgnoreCase(expected3)) return null;
                    }
                    break;
                }
                case "reachableByJr":
                {
                    SourceStatement start = match.opMap.get(Integer.parseInt(constraint[1]));
                    Integer startAddress = start.getAddress(code);
                    SourceConstant sc = code.getSymbol(constraint[2]);
                    if (sc == null) return null;
                    Integer endAddress = sc.getValue(code, false).intValue();
                    if (startAddress == null || endAddress == null) return null;
                    int diff = endAddress - startAddress;
                    if (diff < -128 || diff > 127) return null;
                    break;
                }
                case "regsNotModified":
                {
                    int idx = Integer.parseInt(constraint[1]);
                    List<SourceStatement> statements = new ArrayList<>();
                    if (match.opMap.containsKey(idx)) {
                        statements.add(match.opMap.get(idx));
                    } else if (match.wildCardMap.containsKey(idx)) {
                        statements.addAll(match.wildCardMap.get(idx));
                    } else {
                        return null;
                    }
                    for(int i = 2;i<constraint.length;i++) {
                        String reg = constraint[i];
                        for(SourceStatement s:statements) {
                            if (!regNotModified(s, reg, f, code)) {
                                return null;
                            }
                        }
                        config.debug("regsNotModified " + reg + " satisfied in: " + statements);
                        config.debug("    mapping was: " + match.variables);
                    }
                    break;
                }
                
                default:
                    throw new UnsupportedOperationException("Unknown pattern constraint " + constraint[0]);
            }
        }

        return match;
    }


    public boolean apply(List<SourceStatement> l, PatternMatch match)
    {
        for(int i = 0;i<pattern.size();i++) {
            int key = pattern.get(i).ID;
            if (match.opMap.containsKey(key)) {
                int insertionPoint = l.indexOf(match.opMap.get(key));
                SourceStatement removed = l.remove(insertionPoint);
                for(int j = 0;j<replacement.size();j++) {
                    if (replacement.get(j).ID == pattern.get(i).ID) {
                        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_CPUOP, removed.sl, removed.source, null);
                        s.op = new CPUOp(replacement.get(j).instantiate(match, config));
                        l.add(insertionPoint, s);
                        insertionPoint++;
                    }
                }
            } else if (match.wildCardMap.containsKey(key)) {
                boolean found = false;
                for(int j = 0;j<replacement.size();j++) {
                    if (replacement.get(j).ID == pattern.get(i).ID) {
                        if (!replacement.get(j).isWildcard()) {
                            config.error("Replacing instructions matched with a wildcard is not yet supported!");
                            return false;
                        } else {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    config.error("Removing instructions matched with a wildcard is not yet supported!");
                    return false;
                }
            }
        }
        return true;
    }


    public boolean regNotModified(SourceStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);        
        if (s.type == SourceStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                config.trace("    ret!");
                return false;
            }
            
            CPUOpDependency dep2 = op.checkOutputDependency(dep);
            return dep.equals(dep2);
        } else {
            return true;
        }
    }
    
    
    public boolean regNotUsed(SourceStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);
        return depNotUsed(s, dep, f, code);
    }


    public boolean flagNotUsed(SourceStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);
        return depNotUsed(s, dep, f, code);
    }


    public boolean depNotUsed(SourceStatement s, CPUOpDependency a_dep, SourceFile f, CodeBase code)
    {
        List<Pair<SourceStatement,CPUOpDependency>> open = new ArrayList<>();
        HashMap<SourceStatement,List<CPUOpDependency>> closed = new HashMap<>();
        List<SourceStatement> tmp = f.nextStatements(s, true, code);
        if (tmp == null) {
            // It's hard to tell where is this instruction going to jump,
            // so we act conservatively, and block the optimization:
            config.trace("    unclear next statement after " + s);
            return false;
        }
        for(SourceStatement s2:tmp) {
            open.add(Pair.of(s2, a_dep));
            List<CPUOpDependency> l = new ArrayList<>();
            l.add(a_dep);
            closed.put(s2, l);
        }
        while(!open.isEmpty()) {
            Pair<SourceStatement, CPUOpDependency> pair = open.remove(0);
            SourceStatement next = pair.getLeft();
            CPUOpDependency dep = pair.getRight();
            config.trace("    "+next.sl.lineNumber+": "+next);

            if (next.type == SourceStatement.STATEMENT_CPUOP) {
                CPUOp op = next.op;
                if (op.isRet()) {
                    // It's hard to tell where is this instruction going to jump,
                    // so we act conservatively, and block the optimization:
                    config.trace("    ret!");
                    return false;
                }
                if (op.checkInputDependency(dep)) {
                    // register is actually used!
                    config.trace("    dependency found!");
                    return false;
                }
                dep = op.checkOutputDependency(dep);
                if (dep == null) {
                    config.trace("    dependency broken!");
                } else {
                    // add successors:
                    List<SourceStatement> nextNext_l = next.source.nextStatements(next, true, code);
                    if (nextNext_l == null) {
                        // It's hard to tell where is this instruction going to jump,
                        // so we act conservatively, and block the optimization:
                        config.trace("    unclear next statement after: "+next);
                        return false;
                    }
                    for(SourceStatement nextNext: nextNext_l) {
                        if (!closed.containsKey(nextNext)) {
                            open.add(Pair.of(nextNext, dep));
                            List<CPUOpDependency> l = new ArrayList<>();
                            l.add(dep);
                            closed.put(nextNext, l);
                        } else {
                            List<CPUOpDependency> l = closed.get(nextNext);
                            boolean found = false;
                            for(CPUOpDependency dep2:l) {
                                if (dep.equals(dep2)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                l.add(dep);
                                open.add(Pair.of(nextNext, dep));
                            }
                        }
                    }
                }
            } else {
                // add successors:
                for(SourceStatement nextNext: next.source.nextStatements(next, true, code)) {
                    if (!closed.containsKey(nextNext)) {
                        open.add(Pair.of(nextNext, dep));
                        List<CPUOpDependency> l = new ArrayList<>();
                        l.add(dep);
                        closed.put(nextNext, l);
                    } else {
                        List<CPUOpDependency> l = closed.get(nextNext);
                        boolean found = false;
                        for(CPUOpDependency dep2:l) {
                            if (dep.equals(dep2)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            l.add(dep);
                            open.add(Pair.of(nextNext, dep));
                        }
                    }
                }
            }
        }

        return true;
    }
}

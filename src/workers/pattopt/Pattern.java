/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpDependency;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import util.Pair;

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
    Integer spaceSaving = null; // cache

    public Pattern(String patternString, MDLConfig a_config) throws Exception
    {
        config = a_config;
        int state = 0;  // 0: default, 1: expecting pattern, 2: expecting replacement, 3: expecting constraints
        CodeBase patternCB = new CodeBase(config);

        // parse the pattern:
        String lines[] = patternString.split("\n");
        for(String line:lines) {
            line = line.strip();
            if (line.startsWith("pattern:")) {
                name = line.substring(8).strip();
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
                        String split[] = line.split(",|\\(|\\)");
                        constraints.add(split);
                        break;
                    }
                    default:
                        throw new Exception("Unexpected line parsing a pattern: " + line);
                }
            }
        }        
        
        config.debug("parsed pattern: " + name);
    }
    
    
    public String getName()
    {
        return name;
    }
    
    
    public int getSpaceSaving(PatternMatch match)
    {
        if (spaceSaving != null) return spaceSaving;
        int patternSize = 0;
        int replacementSize = 0;
        for(CPUOpPattern pat:pattern) {
            patternSize += pat.instantiate(match, config).sizeInBytes();
        }
        for(CPUOpPattern pat:replacement) {
            replacementSize += pat.instantiate(match, config).sizeInBytes();
        }
        spaceSaving = patternSize - replacementSize;
        return spaceSaving;
    }
    
    
    public boolean opMatch(CPUOpPattern pat1, CPUOp op2, CodeBase code, PatternMatch match) throws Exception
    {
        if (!pat1.opName.equals(op2.spec.opName)) return false;
        if (pat1.args.size() != op2.args.size()) return false;
        
        for(int i = 0;i<pat1.args.size();i++) {
            Expression arg1 = pat1.args.get(i);
            Expression arg2 = op2.args.get(i);
            // TODO(santi@): this is a very limited form of matching, define proper
            //               expression unification if needed by more complex patterns.
            if (arg1.type == Expression.EXPRESSION_SYMBOL &&
                arg1.symbolName.startsWith("?")) {
                // it's a variable!
                if (arg1.symbolName.startsWith("?reg")) {
                    if (arg2.isRegister(code)) {
                        match.variablesMatched.put(arg1.symbolName, arg2);
                    } else {
                        return false;
                    }
                } else if (arg1.symbolName.startsWith("?const")) {
                    if (arg2.evaluatesToNumericConstant()) {
                        match.variablesMatched.put(arg1.symbolName, arg2);
                    } else {
                        return false;
                    }
                } else if (arg1.symbolName.startsWith("?any")) {
                    match.variablesMatched.put(arg1.symbolName, arg2);
                } else {
                    throw new Exception("opMatch: unrecognized variable name " + arg1.symbolName);
                }
            } else if (arg1.type == Expression.EXPRESSION_PARENTHESIS &&
                       arg1.args.get(0).type == Expression.EXPRESSION_SYMBOL &&
                       arg1.args.get(0).symbolName.startsWith("?")) {
                if (arg1.args.get(0).symbolName.startsWith("?const")) {
                    if (arg2.type == Expression.EXPRESSION_PARENTHESIS &&
                        arg2.args.get(0).evaluatesToNumericConstant()) {
                        match.variablesMatched.put(arg1.args.get(0).symbolName, arg2.args.get(0));
                    } else {
                        return false;
                    }                
                } else {
                    throw new Exception("opMatch: unsupported matching case " + arg1 + " vs " + arg2);
                }
            } else {
                if (!pat1.args.get(i).toString().equals(op2.args.get(i).toString())) return false;
            }
        }
        
        config.debug("opMatch:" + pat1 + " with " + op2 + "    (" + match.variablesMatched + ")");
        
        return true;
    }
    
    
    public PatternMatch match(int a_index, SourceFile f, CodeBase code, MDLConfig config,
                              boolean logPatternsMatchedWithViolatedConstraints) throws Exception
    {
        int index = a_index;
        List<SourceStatement> l = f.getStatements();
        if (l.get(index).type != SourceStatement.STATEMENT_CPUOP) return null;
        PatternMatch match = new PatternMatch();
        for(int i = 0;i<pattern.size();i++) {
            while(true) {
                if (l.get(index).type == SourceStatement.STATEMENT_CPUOP) break;
                if (!l.get(index).isEmptyAllowingComments()) return null;
                index++;
                if (index >= l.size()) return null;
                
            }
            if (!opMatch(pattern.get(i), l.get(index).op, code, match)) return null;
            match.opMap.put(i, l.get(index));
            index++;
        }
        
        // potential match! check constraints:
        for(String[] raw_constraint:constraints) {
            String []constraint = new String[raw_constraint.length];
            for(int i = 0;i<raw_constraint.length;i++) {
                if (match.variablesMatched.containsKey(raw_constraint[i])) {
                    constraint[i] = match.variablesMatched.get(raw_constraint[i]).toString();
                } else {
                    constraint[i] = raw_constraint[i];
                }
            }
            
            switch(constraint[0]) {
                case "regNotUsed":
                {
                    int idx = Integer.parseInt(constraint[1]);
                    for(int i = 2;i<constraint.length;i++) {
                        String reg = constraint[i];

                        if (!regNotUsed(match.opMap.get(idx), reg, f, code)) {
                            if (logPatternsMatchedWithViolatedConstraints)
                                config.info("Potential optimization ("+name+") in " + f.fileName + ", line " + f.getStatements().get(a_index).lineNumber);
                            return null;
                        }
                    }
                    break;
                }
                case "flagsNotUsed":
                {
                    int idx = Integer.parseInt(constraint[1]);
                    for(int i = 2;i<constraint.length;i++) {
                        String flag = constraint[i];

                        if (!flagNotUsed(match.opMap.get(idx), flag, f, code)) {
                            if (logPatternsMatchedWithViolatedConstraints)
                                config.info("Potential optimization ("+name+") in " + f.fileName + ", line " + f.getStatements().get(a_index).lineNumber);
                            return null;
                        }
                    }
                    break;
                }
                default:
                    throw new Exception("Unknown pattern constraint " + constraint[0]);
            }
        }
                
        return match;
    }


    public boolean apply(List<SourceStatement> l, PatternMatch match)
    {
        for(int i = 0;i<pattern.size();i++) {
            int insertionPoint = l.indexOf(match.opMap.get(i));
            SourceStatement removed = l.remove(insertionPoint);
            for(int j = 0;j<replacement.size();j++) {
                if (replacement.get(j).ID == pattern.get(i).ID) {
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_CPUOP, removed.source, removed.lineNumber, null);
                    s.op = new CPUOp(replacement.get(j).instantiate(match, config));
                    l.add(insertionPoint, s);
                    insertionPoint++;
                }
            } 
        }
        return true;
    }
    
    
    public boolean regNotUsed(SourceStatement s, String reg, SourceFile f, CodeBase code) throws Exception
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);
        return depNotUsed(s, dep, f, code);
    }

    
    public boolean flagNotUsed(SourceStatement s, String flag, SourceFile f, CodeBase code) throws Exception
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);
        return depNotUsed(s, dep, f, code);
    }
    
    
    public boolean depNotUsed(SourceStatement s, CPUOpDependency a_dep, SourceFile f, CodeBase code) throws Exception
    {
        List<Pair<SourceStatement,CPUOpDependency>> open = new ArrayList<>();
        HashMap<SourceStatement,List<CPUOpDependency>> closed = new HashMap<>();
        List<SourceStatement> tmp = f.nextStatements(s, true, code);
        if (tmp == null) {
            // It's hard to tell where is this instruction going to jump,
            // so we act conservatively, and block the optimization:
            config.debug("    unclear next statement after " + s);
            return false;
        }
        for(SourceStatement s2:tmp) {
            open.add(new Pair<>(s2, a_dep));
            List<CPUOpDependency> l = new ArrayList<>();
            l.add(a_dep);
            closed.put(s2, l);
        }
        while(!open.isEmpty()) {
            Pair<SourceStatement, CPUOpDependency> pair = open.remove(0);
            SourceStatement next = pair.m_a;
            CPUOpDependency dep = pair.m_b;
            config.debug("    " + next.lineNumber + ": " + next);
            
            if (next.type == SourceStatement.STATEMENT_CPUOP) {
                CPUOp op = next.op;
                if (op.isRet()) {
                    // It's hard to tell where is this instruction going to jump,
                    // so we act conservatively, and block the optimization:
                    config.debug("    ret!");
                    return false;
                }
                if (op.checkInputDependency(dep)) {
                    // register is actually used!
                    config.debug("    dependency found!");
                    return false;
                }
                dep = op.checkOutputDependency(dep);
                if (dep == null) {
                    config.debug("    dependency broken!");
                } else {
                    // add successors:
                    List<SourceStatement> nextNext_l = next.source.nextStatements(next, true, code);
                    if (nextNext_l == null) {
                        // It's hard to tell where is this instruction going to jump,
                        // so we act conservatively, and block the optimization:
                        config.debug("    unclear next statement after: " + next);
                        return false;
                    }
                    for(SourceStatement nextNext: nextNext_l) {
                        if (!closed.containsKey(nextNext)) {
                            open.add(new Pair<>(nextNext, dep));
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
                                open.add(new Pair<>(nextNext, dep));
                            }
                        }
                    }
                }
            } else {
                // add successors:
                for(SourceStatement nextNext: next.source.nextStatements(next, true, code)) {
                    if (!closed.containsKey(nextNext)) {
                        open.add(new Pair<>(nextNext, dep));
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
                            open.add(new Pair<>(nextNext, dep));
                        }
                    }
                }                
            }
        }
        
        return true;
    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers.pattopt;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.SourceFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author santi
 */
public class ExecutionFlowAnalysis {
    MDLConfig config;
    CodeBase code;
    
    HashMap<CodeStatement, List<CodeStatement>> forwardTable = null;
    HashMap<CodeStatement, List<CodeStatement>> reverseTable = null;
        
    
    public ExecutionFlowAnalysis(CodeBase a_code, MDLConfig a_config) {
        config = a_config;
        code = a_code;
    }


    public void clear()
    {
        forwardTable = null;
        reverseTable = null;
    }


    public List<CodeStatement> nextOpExecutionStatements(CodeStatement s)
    {
        return nextOpExecutionStatements(s.source.getStatements().indexOf(s), s.source);
    }
    
    
    public List<CodeStatement> nextOpExecutionStatements(int index, SourceFile f)
    {
        List<CodeStatement> open = new ArrayList<>();
        List<Pair<CodeStatement, List<CodeStatement>>> next = f.nextExecutionStatements(index, true, new ArrayList<>(), code);
        if (next == null) return null;

        List<CodeStatement> nextOpStatements = new ArrayList<>();
        for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
            open.add(tmp.getLeft());
        }
        while(!open.isEmpty()) {
            CodeStatement s = open.remove(0);
            if (s.op != null) {
                if (!nextOpStatements.contains(s)) {
                    nextOpStatements.add(s);
                }
            } else {
                next = f.nextExecutionStatements(s, true, new ArrayList<>(), code);
                if (next == null) return null;
                for(Pair<CodeStatement, List<CodeStatement>> tmp:next) {
                    open.add(tmp.getLeft());
                }
            }
        }
        return nextOpStatements;
    }
    
    
    public Pair<HashMap<CodeStatement, List<CodeStatement>>,
                HashMap<CodeStatement, List<CodeStatement>>> generateForwardAndReverseJumpTables()
    {
        if (forwardTable != null) {
            return Pair.of(forwardTable, reverseTable);
        }
        forwardTable = new HashMap<>();
        reverseTable = new HashMap<>();
        
        for(SourceFile f:code.getSourceFiles()) {
            for(int i = 0;i<f.getStatements().size();i++) {
                if (f.getStatements().get(i).op != null) {
                    CodeStatement s_i = f.getStatements().get(i);
                    if (s_i.op.isRet()) continue;
                    List<CodeStatement> next = nextOpExecutionStatements(i, f);
                    if (next == null) return null;
                    forwardTable.put(s_i, next);
                    for(CodeStatement s:next) {
                        if (!reverseTable.containsKey(s)) {
                            reverseTable.put(s, new ArrayList<>());
                        }
                        if (!reverseTable.get(s).contains(f.getStatements().get(i))) {
                            reverseTable.get(s).add(f.getStatements().get(i));
                        }
                    }
                }
            }
        }
        
        return Pair.of(forwardTable, reverseTable);
    }
                
                
    public HashMap<CodeStatement, List<CodeStatement>> findAllRetDestinations()
    {
        HashMap<CodeStatement, List<CodeStatement>> forwardTableOnlyRets = new HashMap<>();
                
        for(SourceFile f:code.getSourceFiles()) {
            for(CodeStatement s:f.getStatements()) {
                if (s.op != null && s.op.isRet()) {
                    config.debug("findAllRetDestinations: found ret: " + s);
                    List<CodeStatement> destinations = findRetDestinations(s);
                    forwardTableOnlyRets.put(s, destinations);
                    if (destinations == null) {
                        config.debug("findAllRetDestinations: null for " + s);
                    } else {
                        config.debug("findAllRetDestinations: " + destinations.size() + " for " + s);
                    }
                }
            }
        }
        
        return forwardTableOnlyRets;
    }
                    
                       
    public List<CodeStatement> findRetDestinations(CodeStatement s)
    {
        if (s.op == null || !s.op.isRet()) {
            config.error("allPossibleReturnStatementsForRet called for " + s);
            return null;
        }

        // make sure the tables are generated:
        generateForwardAndReverseJumpTables();
        
        if (forwardTable.containsKey(s)) return forwardTable.get(s);
        if (!reverseTable.containsKey(s)) return null;
        
        List<CodeStatement> possibleDestinations = new ArrayList<>();
        
        HashSet<CodeStatement> closed = new HashSet<>();
        List<CodeStatement> open = new ArrayList<>();
        open.addAll(reverseTable.get(s));
        while(!open.isEmpty()) {
            CodeStatement s2 = open.remove(0);
            closed.add(s2);
            if (s2.op.isCall()) {
                CodeStatement s2_next = s2.source.getNextStatementTo(s2, code);
                if (s2_next != null) {
                    if (s2_next.op == null) {
                        // Find the next op:
                        List<CodeStatement> s2_next_op_l = nextOpExecutionStatements(s2_next);
                        if (s2_next_op_l == null || s2_next_op_l.size() > 1) {
                            config.error("findRetDestinations: failed to find the next op after " + s.fileNameLineString());
                        } else {
                            possibleDestinations.add(s2_next_op_l.get(0));
                        }
                    } else {
                        possibleDestinations.add(s2_next);
                    }
                }
            } else {
                // keep going backwards:
                List<CodeStatement> prev_l = reverseTable.get(s2);
                for(CodeStatement prev:prev_l) {
                    if (!closed.contains(prev) && !open.contains(prev)) {
                        open.add(prev);
                    }
                }
            }
        }
        
        forwardTable.put(s, possibleDestinations);
        return possibleDestinations;
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import code.CPUOp;
import code.Expression;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class SequenceEquivalence {
    String key1 = null, key2 = null;
    List<SBOCandidate> s1, s2;
    List<String> flagsToIgnore;
    int size1, size2;
    int time1, time2;
    
    List<String> alternativeOps = new ArrayList<>();
    List<String> alternativeRegs = new ArrayList<>();
    
    
    public SequenceEquivalence(List<SBOCandidate> a_s1, List<SBOCandidate> a_s2, List<String> a_flagsToIgnore)
    {
        s1 = a_s1;
        s2 = a_s2;
        flagsToIgnore = a_flagsToIgnore;
        size1 = 0;
        size2 = 0;
        time1 = 0;
        time2 = 0;
        for(SBOCandidate s:s1) {
            size1 += s.op.sizeInBytes();
            time1 += s.op.timing()[0];
        }
        for(SBOCandidate s:s2) {
            size2 += s.op.sizeInBytes();
            time2 += s.op.timing()[0];
            
            if (!alternativeOps.contains(s.op.spec.getName())) {
                alternativeOps.add(s.op.spec.getName());
            }
            for(Expression arg:s.op.args) {
                for(String reg:arg.getAllRegisters()) {
                    if (!alternativeRegs.contains(reg)) {
                        alternativeRegs.add(reg);
                    }
                }
            }
        }
        key1 = getKeySBO(a_s1);
        key2 = getKeySBO(a_s2);
        
    }    
    
    
    public boolean flagsToIgnoreCheck(Specification spec)
    {
        for(String flag:flagsToIgnore) {
            if (flag.equals("H") || flag.equals("N")) {
                if (spec.allowedOps.contains("daa")) return false;
            }
            if (flag.equals("P/V")) {
                if (spec.allowedOps.contains("jp") ||
                    spec.allowedOps.contains("call") ||
                    spec.allowedOps.contains("ret")) return false;
            }
        }
        
        return true;
    }
    
    
    public boolean alternativeCanBeBetter()
    {
        if (alternativeIsBetter(SearchBasedOptimizer.SEARCH_ID_BYTES) ||
            alternativeIsBetter(SearchBasedOptimizer.SEARCH_ID_OPS) ||
            alternativeIsBetter(SearchBasedOptimizer.SEARCH_ID_CYCLES)) return true;
        return false;
    }
    
    
    public boolean alternativeIsBetter(int searchType)
    {
        if (searchType == SearchBasedOptimizer.SEARCH_ID_BYTES) {
            if (size2 < size1) return true;
            if (size2 > size1) return false;
            if (time2 < time1) return true;
            if (time2 > time1) return false;
            // cannonical order otherwise:
            return key1.compareTo(key2) < 0;
        } else if (searchType == SearchBasedOptimizer.SEARCH_ID_OPS) {
            if (s2.size() < s1.size()) return true;
            if (s2.size() > s1.size()) return false;
            if (time2 < time1) return true;
            if (time2 > time1) return false;
            if (size2 < size1) return true;
            if (size2 > size1) return false;
            // cannonical order otherwise:
            return key1.compareTo(key2) < 0;
        } else {
            // time:
            if (time2 < time1) return true;
            if (time2 > time1) return false;
            if (size2 < size1) return true;
            if (size2 > size1) return false;
            // cannonical order otherwise:
            return key1.compareTo(key2) < 0;
        }
    }
    
    
    public boolean alternativeAllowed(Specification spec) 
    {
        for(String op:alternativeOps) {
            if (!spec.allowedOps.contains(op)) return false;
        }
        for(String reg:alternativeRegs) {
            if (!spec.allowedRegisters.contains(reg)) return false;
        }
        return true;
    }
    
    
    public String getKey()
    {
        if (key1 != null) return key1;
        key1 = getKeySBO(s1);
        return key1;
    }
    
    
    public static String getKeySBO(List<SBOCandidate> seq)
    {
        String key = "";
        for(SBOCandidate s:seq) {
            key += s.toString() + "; ";
        }
        return key;
    }


    public static String getKey(List<CPUOp> seq)
    {
        String key = "";
        for(CPUOp s:seq) {
            key += s.toString() + "; ";
        }
        return key;
    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers.pattopt;

import code.CPUOpDependency;
import code.CodeStatement;
import java.util.List;

/**
 *
 * @author santi
 */
public class DepCheckNode {
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

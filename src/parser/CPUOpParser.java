/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpSpec;
import code.CodeBase;
import code.Expression;
import code.SourceStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author santi
 */
public class CPUOpParser {
    MDLConfig config;

    List<CPUOpSpec> opSpecs;
    HashMap<String, List<CPUOpSpec>> opSpecHash = new HashMap<>();    
    
    
    public CPUOpParser(List<CPUOpSpec> a_opSpecs, MDLConfig a_config) {
        opSpecs = a_opSpecs;
        config = a_config;
        
        for(CPUOpSpec spec:opSpecs) {
            if (!opSpecHash.containsKey(spec.getName())) {
                opSpecHash.put(spec.getName(), new ArrayList<>());
            }
            opSpecHash.get(spec.getName()).add(spec);
        }        
    }
    
    
    public boolean isOpName(String name)
    {
        return opSpecHash.containsKey(name);
    }
    
    
    public List<CPUOpSpec> getOpSpecs(String name)
    {
        List<CPUOpSpec> l = opSpecHash.get(name);
        if (l != null) return l;
        return new ArrayList<>();
    }
    
    
    public CPUOp parseOp(String a_op, List<Expression> a_args, SourceStatement s, CodeBase code) 
    {
        CPUOp op;
        for(CPUOpSpec opSpec:getOpSpecs(a_op)) {
            op = parseOp(a_op, opSpec, a_args, s, code);
            if (op != null) return op;
        }
        return null;
    }
    
    
    public CPUOp parseOp(String a_op, CPUOpSpec spec, List<Expression> a_args, SourceStatement s, CodeBase code)
    {
        if (!a_op.equalsIgnoreCase(spec.opName)) return null;
        if (spec.args.size() != a_args.size()) return null;
        for(int i = 0; i<spec.args.size(); i++) {
            if (!spec.args.get(i).match(a_args.get(i), spec, s, code)) {
                return null;
            }
        }
        
        if (spec.isJpRegWithParenthesis && config.warningJpHlWithParenthesis) {
            config.warn("Use of confusing classic 'jp (hl)' syntax, rather than the more accurate 'jp hp' in " + 
                    s.source.fileName + ", " + s.lineNumber);
        }
        
        return new CPUOp(spec, a_args);
    }        
}

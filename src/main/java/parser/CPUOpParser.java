/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpSpec;
import code.CPUOpSpecArg;
import code.CodeBase;
import code.Expression;
import code.SourceStatement;

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
        return opSpecHash.containsKey(name.toLowerCase());
    }


    public List<CPUOpSpec> getOpSpecs(String name)
    {
        List<CPUOpSpec> l = opSpecHash.get(name.toLowerCase());
        if (l != null) return l;
        return new ArrayList<>();
    }


    public CPUOp parseOp(String a_op, List<Expression> a_args, SourceStatement s, CodeBase code)
    {
        CPUOp op;
        List<CPUOpSpec> candidates = getOpSpecs(a_op);
        for(CPUOpSpec opSpec:candidates) {
            op = parseOp(a_op, opSpec, a_args, s, code);
            if (op != null) return op;
        }
        if (candidates != null && !candidates.isEmpty()) {
            // try to see if any of the arguments was a constant with parenthesis that had been interpreted
            // as an indirection:
            boolean anyChange = false;
            for(int i = 0;i<a_args.size();i++) {
                Expression arg = a_args.get(i);
                if (arg.evaluatesToIntegerConstant() && arg.type == Expression.EXPRESSION_PARENTHESIS) {
                    a_args.set(i, arg.args.get(0));
                    anyChange = true;
                }
            }
            if (anyChange) {
                // try again!
                for(CPUOpSpec opSpec:candidates) {
                    op = parseOp(a_op, opSpec, a_args, s, code);
                    if (op != null) return op;
                }
            }
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
            config.warn("Style suggestion", s.fileNameLineString(),
                    "Prefer using 'jp reg' rather than the confusing z80 'jp (reg)' syntax.");
        }

        if (!spec.official) {
            if (config.convertToOfficial) {
                CPUOp unofficial = new CPUOp(spec, a_args, config);
                CPUOp official = officialFromUnofficial(spec.officialEquivalent, spec, a_args, code);
                if (config.warningUnofficialOps) {
                    config.warn("Style suggestion", s.fileNameLineString(), "Unofficial op syntax: " + unofficial + " converted to " + official);
                }                
                return official;
            } else {
                if (config.warningUnofficialOps) {
                    config.warn("Style suggestion", s.fileNameLineString(), "Unofficial op syntax: " + new CPUOp(spec, a_args, config));
                }    
            }
        }    
        return new CPUOp(spec, a_args, config);
    }        
    
    
    CPUOp officialFromUnofficial(CPUOpSpec officialSpec, CPUOpSpec unofficialSpec, List<Expression> a_args, CodeBase code)
    {
        List<Expression> officialArgs = new ArrayList<>();

        if (unofficialSpec.opName.equalsIgnoreCase("sub") && a_args.size() == 2) {
            // just ignore the initial "A":
            officialArgs.add(a_args.get(1));
            return new CPUOp(officialSpec, officialArgs, config);
        }
        
        List<Integer> used = new ArrayList<>();
        for(CPUOpSpecArg officialArgSpec:officialSpec.args) {
            boolean found = false;
            for(int i = 0;i<unofficialSpec.args.size();i++) {
                if (used.contains(i)) continue;
                if (officialArgSpec.equals(unofficialSpec.args.get(i))) {
                    officialArgs.add(a_args.get(i));
                    used.add(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // if the missing argument is a register, that is precisely specified (in upper case), we fill it in:
                if (officialArgSpec.reg != null && 
                    officialArgSpec.reg.equals(officialArgSpec.reg.toUpperCase())) {
                    officialArgs.add(Expression.symbolExpression(officialArgSpec.reg.toLowerCase(), code, config));
                } else {
                    // case not supported:
                    config.error("Cannot turn unofficial assembler op to official!");
                    return null;
                }
            }
        }
        
        return new CPUOp(officialSpec, officialArgs, config);
    }
}

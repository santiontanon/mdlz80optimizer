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
import code.CodeStatement;

/**
 *
 * @author santi
 */
public class CPUOpParser {
    MDLConfig config;

    List<CPUOpSpec> opSpecs;
    HashMap<String, List<CPUOpSpec>> opSpecHash = new HashMap<>();
    
    public boolean indirectionsOnlyWithSquareBrackets = false;
    public boolean indirectionsOnlyWithParenthesis = false;
    
    public boolean allowExtendedSjasmplusLDInstructions = false;


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
    
    
    public void addOpSpec(CPUOpSpec fake)
    {
        List<CPUOpSpec> l = opSpecHash.get(fake.opName);
        if (l == null) {
            l = new ArrayList<>();
            opSpecHash.put(fake.opName, l);
        }
        l.add(fake);
    }


    public boolean isOpName(String name)
    {
        return opSpecHash.containsKey(name.toLowerCase());
    }

    
    public boolean isOpName(String name, int nArgs)
    {
        if (!opSpecHash.containsKey(name.toLowerCase())) return false;
        for(CPUOpSpec spec:opSpecHash.get(name.toLowerCase())) {
            if (spec.args.size() == nArgs) return true;
        }
        return false;
    }
    

    public List<CPUOpSpec> getOpSpecs(String name)
    {
        List<CPUOpSpec> l = opSpecHash.get(name.toLowerCase());
        if (l != null) return l;
        return new ArrayList<>();
    }


    // "previous" is used for label scoping (it should be the statement that will be right before "s", after inserting "s"
    // into the SourceFile, since "s" might not have been yet inserted into it:    
    public List<CPUOp> parseOp(String a_op, List<Expression> a_args, CodeStatement s, CodeStatement previous, CodeBase code)
    {
        List<CPUOpSpec> candidates = getOpSpecs(a_op);
        for(CPUOpSpec opSpec:candidates) {
            List<CPUOp> l = parseOp(a_op, opSpec, a_args, s, previous, code);
            if (l != null) {
                return l;
            }
        }
        if (candidates != null && !candidates.isEmpty()) {
            // see if it's an extended "ld" instruction:
            if (allowExtendedSjasmplusLDInstructions &&
                a_op.equalsIgnoreCase("ld") && a_args.size() >= 2 && a_args.size()%2 == 0) {
                List<CPUOp> l = new ArrayList<>();
                for(int i = 0;i<a_args.size();i+=2) {
                    List<Expression> argsAux = new ArrayList<>();
                    argsAux.add(a_args.get(i));
                    argsAux.add(a_args.get(i+1));
                    boolean found = false;
                    for(CPUOpSpec opSpec:candidates) {
                        List<CPUOp> l2 = parseOp(a_op, opSpec, argsAux, s, previous, code);
                        if (l2 != null) {
                            l.addAll(l2);
                            found = true;
                        }
                    }
                    if (!found) {
                        l = null;
                        break;
                    }
                }
                if (l != null) return l;
            }
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
                    List<CPUOp> l = parseOp(a_op, opSpec, a_args, s, previous, code);
                    if (l != null) return l;
                }
            }
        }

        return null;
    }


    public List<CPUOp> parseOp(String a_op, CPUOpSpec spec, List<Expression> a_args, CodeStatement s, CodeStatement previous, CodeBase code)
    {
        if (!a_op.equalsIgnoreCase(spec.opName)) return null;
        if (spec.args.size() != a_args.size()) return null;
        
        // replace S/NS by M/P as flags:
        if (spec.opName.equalsIgnoreCase("ret") && !a_args.isEmpty()) {
            if (a_args.get(0).type == Expression.EXPRESSION_SYMBOL) {
                if (a_args.get(0).symbolName.equalsIgnoreCase("s")) {
                    a_args.get(0).type = Expression.EXPRESSION_REGISTER_OR_FLAG;
                    a_args.get(0).registerOrFlagName = "m";
                } else if (a_args.get(0).symbolName.equalsIgnoreCase("ns")) {
                    a_args.get(0).type = Expression.EXPRESSION_REGISTER_OR_FLAG;
                    a_args.get(0).registerOrFlagName = "p";
                }
            }
        } else if ((spec.opName.equalsIgnoreCase("call") ||
                    spec.opName.equalsIgnoreCase("jp")) && a_args.size() == 2) {
            if (a_args.get(0).type == Expression.EXPRESSION_SYMBOL) {
                if (a_args.get(0).symbolName.equalsIgnoreCase("s")) {
                    a_args.get(0).type = Expression.EXPRESSION_REGISTER_OR_FLAG;
                    a_args.get(0).registerOrFlagName = "m";
                } else if (a_args.get(0).symbolName.equalsIgnoreCase("ns")) {
                    a_args.get(0).type = Expression.EXPRESSION_REGISTER_OR_FLAG;
                    a_args.get(0).registerOrFlagName = "p";
                }
            }            
        }
        
        
        for(int i = 0; i<spec.args.size(); i++) {
            // for some reason, asMSX (which uses square brackets for indirections),
            // does not impose this constraint for in/out ops.... ¯\_(ツ)_/¯
            if (indirectionsOnlyWithSquareBrackets && 
                !a_op.equalsIgnoreCase("out") &&
                !a_op.equalsIgnoreCase("in")) {
                while(a_args.get(i).type == Expression.EXPRESSION_PARENTHESIS &&
                      a_args.get(i).parenthesis.equals("(") &&
                      a_args.get(i).args != null &&
                      a_args.get(i).args.size() == 1) {
                    a_args.set(i, a_args.get(i).args.get(0));
                }
            }
            if (indirectionsOnlyWithParenthesis && 
                !a_op.equalsIgnoreCase("out") &&
                !a_op.equalsIgnoreCase("in")) {
                while(a_args.get(i).type == Expression.EXPRESSION_PARENTHESIS &&
                      a_args.get(i).parenthesis.equals("[") &&
                      a_args.get(i).args != null &&
                      a_args.get(i).args.size() == 1) {
                    a_args.set(i, a_args.get(i).args.get(0));
                }
            }
            if (!spec.args.get(i).match(a_args.get(i), spec, s, code)) {
                return null;
            }
        }

        if (spec.isJpRegWithParenthesis && config.warning_jpHlWithParenthesis) {
            config.warn("Style suggestion", s.fileNameLineString(),
                    "Prefer using 'jp reg' rather than the confusing z80 'jp (reg)' syntax.");
        }
        
        if (spec.fakeInstructionEquivalent != null) {
            // it's a fake instruction!
            List<CPUOp> l = new ArrayList<>();
            
            for(List<String> tokens_raw:spec.fakeInstructionEquivalent) {
                List<String> tokens = new ArrayList<>();
                for(String token:tokens_raw) {
                    if (token.equals("o")) {
                        // replace it with the value in the args:
                        if (spec.args.get(0).regOffsetIndirection != null) {
                            Expression exp = null;
                            switch (a_args.get(0).args.get(0).type) {
                                case Expression.EXPRESSION_SUM:
                                    exp = a_args.get(0).args.get(0).args.get(1);
                                    break;
                                case Expression.EXPRESSION_SUB:
                                    if (!tokens.remove(tokens.size()-1).equals("+")) {
                                        return null;
                                    }   tokens.add("-");
                                    exp = a_args.get(0).args.get(0).args.get(1);
                                    break;
                                default:
                                    if (!tokens.remove(tokens.size()-1).equals("+")) {                                
                                        return null;
                                    }   break;
                            }
                            if (exp != null) {
                                tokens.addAll(Tokenizer.tokenize(exp.toString()));
                            }
                        } else if (spec.args.get(1).regOffsetIndirection != null) {
                            Expression exp = null;
                            switch (a_args.get(1).args.get(0).type) {
                                case Expression.EXPRESSION_SUM:
                                    exp = a_args.get(1).args.get(0).args.get(1);
                                    break;
                                case Expression.EXPRESSION_SUB:
                                    if (!tokens.remove(tokens.size()-1).equals("+")) {
                                        return null;
                                    }   tokens.add("-");
                                    exp = a_args.get(1).args.get(0).args.get(1);
                                    break;
                                default:
                                    if (!tokens.remove(tokens.size()-1).equals("+")) {                                
                                        return null;
                                    }   break;
                            }
                            if (exp != null) {
                                tokens.addAll(Tokenizer.tokenize(exp.toString()));
                            }
                        } else {
                            return null;
                        }
                    } else if (token.equals("nn")) {
                        // we assume this occurs only as the second argument:
                        tokens.addAll(Tokenizer.tokenize(a_args.get(1).toString()));
                    } else {
                        tokens.add(token);
                    }
                }
                String opName = tokens.remove(0);
                List<Expression> arguments = new ArrayList<>();
                while (!tokens.isEmpty()) {
                    if (Tokenizer.isSingleLineComment(tokens.get(0))) break;
                    Expression exp = config.expressionParser.parse(tokens, s, previous, code);
                    if (exp == null) return null;
                    arguments.add(exp);
                    if (!tokens.isEmpty() && tokens.get(0).equals(",")) {
                        tokens.remove(0);
                    } else {
                        break;
                    }
                }

                List<CPUOp> op_l = parseOp(opName, arguments, s, previous, code);                
                if (op_l == null || op_l.size() != 1) {
                    config.error("Cannot parse fake instruction replacement for token list: " + tokens_raw);
                    return null;
                }
                l.add(op_l.get(0));
            }
            
            return l;
        }

        if (!spec.official) {
            if (config.convertToOfficial) {
                CPUOp unofficial = new CPUOp(spec, a_args, config);
                CPUOp official = officialFromUnofficial(spec.officialEquivalent, spec, a_args, code);
                if (config.warning_unofficialOps) {
                    config.warn("Style suggestion", s.fileNameLineString(), "Unofficial op syntax: " + unofficial + " converted to " + official);
                }                
                List<CPUOp> l = new ArrayList<>();
                l.add(official);
                return l;
            } else {
                if (config.warning_unofficialOps) {
                    config.warn("Style suggestion", s.fileNameLineString(), "Unofficial op syntax: " + new CPUOp(spec, a_args, config));
                }    
            }
        }
        List<CPUOp> l = new ArrayList<>();
        l.add(new CPUOp(spec, a_args, config));
        return l;
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
                    officialArgs.add(Expression.symbolExpression(officialArgSpec.reg.toLowerCase(), null, code, config));
                } else {
                    // case not supported:
                    config.error("Cannot turn unofficial assembler op to official: " + officialSpec.opName + " with args: " + a_args);
                    return null;
                }
            }
        }
        
        return new CPUOp(officialSpec, officialArgs, config);
    }
}

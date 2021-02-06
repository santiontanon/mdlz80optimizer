/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.util.ArrayList;
import java.util.List;
import parser.Tokenizer;

public class CPUOp {
    MDLConfig config;
    public CPUOpSpec spec;
    public List<Expression> args;

    List<CPUOpDependency> inputDeps = null;
    List<CPUOpDependency> outputDeps = null;


    public CPUOp(CPUOpSpec a_spec, List<Expression> a_args, MDLConfig a_config)
    {
        spec = a_spec;
        args = a_args;
        config = a_config;
    }


    public CPUOp(CPUOp op)
    {
        spec = op.spec;
        args = new ArrayList<>();
        args.addAll(op.args);
        config = op.config;
    }


    public int sizeInBytes()
    {
        return spec.sizeInBytes;
    }

    
    public int[] timing()
    {
        return spec.times;
    }
    

    public String timeString()
    {
        return spec.timeString();
    }


    @Override
    public String toString()
    {
        String str = spec.opName;
        
        if (config.output_opsInLowerCase) str = str.toLowerCase();

        for(int i = 0;i<args.size();i++) {
            if (i==0) {
                str += " ";
            } else {
                str += ", ";
            }
            if (config.output_indirectionsWithSquareBrakets && 
                (spec.args.get(i).regIndirection != null ||
                 spec.args.get(i).regOffsetIndirection != null ||
                 spec.args.get(i).byteConstantIndirectionAllowed ||
                 spec.args.get(i).wordConstantIndirectionAllowed)) {
                if (args.get(i).type == Expression.EXPRESSION_PARENTHESIS &&
                    args.get(i).args.size()==1) {
                    str += "["+args.get(i).args.get(0).toString()+"]";
                } else {
                    str += args.get(i).toString();
                }
            } else {
                str += args.get(i).toString();
            }
        }

        return str;
    }


    public boolean checkInputDependency(CPUOpDependency dep)
    {
        getInputDependencies();

        for(CPUOpDependency inDep:inputDeps) {
            if (dep.match(inDep)) {
                return true;
            }
        }

        return false;
    }


    /*
    Given a dependency "dep" that comes from an earlier instruction,
    this function removes all the info from "dep" that the current instruction
    overwrites. Returning "null" if "dep" becomes empty after removing info.
    */
    public CPUOpDependency checkOutputDependency(CPUOpDependency dep)
    {
        getOutputDependencies();
        
        // remove from "dep" all the dependencies that are overwritten:
        CPUOpDependency dep2 = new CPUOpDependency(dep);
        for(CPUOpDependency outDep:outputDeps) {
            dep2.remove(outDep);
        }
        
        if (dep2.isEmpty()) return null;

        return dep2;
    }


    public List<CPUOpDependency> getInputDependencies()
    {
        if (inputDeps == null) inputDeps = spec.getInputDependencies(args);
        return inputDeps;
    }


    public List<CPUOpDependency> getOutputDependencies()
    {
        if (outputDeps == null) outputDeps = spec.getOutputDependencies(args);
        return outputDeps;
    }


    public Expression getTargetJumpExpression()
    {
        int labelArg = spec.jumpLabelArgument();
        if (labelArg == -1) return null;
        Expression targetLabel = args.get(labelArg);
        return targetLabel;
    }


    public SourceConstant getTargetJumpLabel(CodeBase code)
    {
        int labelArg = spec.jumpLabelArgument();
        if (labelArg == -1) return null;
        Expression targetLabel = args.get(labelArg);
        if (targetLabel.type == Expression.EXPRESSION_SYMBOL) {
            SourceConstant label = code.getSymbol(targetLabel.symbolName);
            return label;
        }
        return null;
    }


    public boolean isConditional()
    {
        return spec.isConditional();
    }


    public boolean isRet()
    {
        return spec.isRet();
    }
    
    
    public boolean isRst()
    {
        return spec.isRst();
    }
    
    
    public boolean mightJump()
    {
        return spec.mightJump();
    }


    public boolean isCall()
    {
        return spec.isCall();
    }

    
    public boolean isJump()
    {
        return spec.isJump();
    }
    
    
    public boolean isPush()
    {
        return spec.isPush();
    }


    public boolean isPop()
    {
        return spec.isPop();
    }

    
    /*
    Returns true if this is an instruction that modifies SP or contents, other than the
    standard call/ret/push/pop. Specifically, it will return true if the instruction
    is one of the following:
    - inc/dec sp
    - ex (sp),???
    - ld sp,???
    - rst ???    
    */
    public boolean modifiesStackInNonStandardWay()
    {
        if (spec.isRst()) return true;
        if (!args.isEmpty() &&
            args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
            args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
            if (spec.opName.equalsIgnoreCase("inc") ||
                spec.opName.equalsIgnoreCase("dec") ||
                spec.opName.equalsIgnoreCase("ld")) return true;
        }
        if (!args.isEmpty() &&
            spec.opName.equalsIgnoreCase("ex") &&
            args.get(0).type == Expression.EXPRESSION_PARENTHESIS &&
            args.get(0).args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
            args.get(0).args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
            return true;
        }
        return false;
    }

    
    public boolean evaluateAllExpressions(SourceStatement s, CodeBase code, MDLConfig config)
    {
        for(int i = 0;i<args.size();i++) {
            // preserve indirections:
            if (args.get(i).type == Expression.EXPRESSION_PARENTHESIS) {
                if (args.get(i).args.get(0).evaluatesToIntegerConstant()) {
                    args.get(i).args.set(0, Expression.constantExpression(args.get(i).args.get(0).evaluateToInteger(s, code, false), config));
                }
            } else {
                if (args.get(i).evaluatesToIntegerConstant()) {
                    Integer value = args.get(i).evaluateToInteger(s, code, false);
                    if (value == null) {
                        config.error("Cannot evaluate expression: " + args.get(i));
                        return false;
                    }
                    args.set(i, Expression.constantExpression(value, config));
                }
            }
        }
        return true;
    }    
    
    
    public List<Integer> assembleToBytes(SourceStatement s, CodeBase code, MDLConfig config)
    {
        List<Integer> data = new ArrayList<>();
        int nn_state = 0;
        
        for(String v[]:spec.bytesRepresentation) {
            int baseByte = Tokenizer.parseHex(v[0]);
            if (v[1].equals("")) {
                data.add(baseByte);
            } else if (v[1].equals("o")) {
                // jr/djnz offset:
                if (spec.isJump()) {
                    int base = s.getAddress(code) + spec.sizeInBytes;
                    int target = args.get(args.size()-1).evaluateToInteger(s, code, true);
                    int offset = (target - base)&0xff;
                    data.add(offset);
                } else {
                    // ld IX/IY offet:
                    int arg_idx = -1;
                    for(int i = 0;i<args.size();i++) {
                        if (spec.args.get(i).regOffsetIndirection != null) {
                            arg_idx = i;
                        }
                    }
                    if (arg_idx == -1) {
                        config.error("Unable to convert " + this + " to bytes! Could not find argument with an indirection with offset");
                        return null;
                    }
                    Integer o = null;
                    if (args.get(arg_idx).type == Expression.EXPRESSION_PARENTHESIS) {
                        Expression arg = args.get(arg_idx).args.get(0);
                        if (arg.type == Expression.EXPRESSION_SUM) {
                            o = arg.args.get(1).evaluateToInteger(s, code, true);
                        } else if (arg.type == Expression.EXPRESSION_SUB) {
                            o = arg.args.get(1).evaluateToInteger(s, code, true);
                            if (o != null) o = -o;
                        } else if (arg.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                            o = 0;
                        }
                    }
                    if (o == null) {
                        config.error("Unable to convert " + this + " to bytes! Could not find the offset");
                        return null;
                    }
                    data.add(o);
                }
                
            } else if (v[1].equals("n")) {
                // 8 bit argument
                Integer n = null;
                for(Expression arg:args) {
                    if (arg.evaluatesToIntegerConstant()) {
                        if (n != null) {
                            config.error("Unable to convert " + this + " to bytes! two numeric constants in one op!");
                            return null;
                        }
                        n = arg.evaluateToInteger(s, code, true);
                    }
                }
                if (n == null) {
                    config.error("Unable to convert " + this + " to bytes! no numeric constants in the op!");
                    return null;
                }
                data.add(n & 0xff);
                
            } else if (v[1].equals("nn")) {
                // 16 bit argument
                Integer nn = null;
                for(Expression arg:args) {
                    if (arg.evaluatesToIntegerConstant()) {
                        if (nn != null) {
                            config.error("Unable to convert " + this + " to bytes! two numeric constants in one op!");
                            return null;
                        }
                        nn = arg.evaluateToInteger(s, code, true);
                    }
                }
                if (nn == null) {
                    config.error("Unable to convert " + this + " to bytes! no numeric constants in the op!");
                    return null;
                }
                if (nn_state == 0) {
                    data.add(nn & 0xff);
                    nn_state = 1;
                } else {
                    data.add((nn >> 8) & 0xff);
                }
                
            } else if (v[1].equals("+0")) {
                data.add(baseByte+0);
            } else if (v[1].equals("+1")) {
                data.add(baseByte+1);
            } else if (v[1].equals("+2")) {
                data.add(baseByte+2);
            } else if (v[1].equals("+3")) {
                data.add(baseByte+3);
            } else if (v[1].equals("+4")) {
                data.add(baseByte+4);
            } else if (v[1].equals("+5")) {
                data.add(baseByte+5);
            } else if (v[1].equals("+6")) {
                data.add(baseByte+6);
            } else if (v[1].equals("+7")) {
                data.add(baseByte+7);

            } else if (v[1].equals("+r") ||
                       v[1].equals("+p") ||
                       v[1].equals("+q")) {
                // register (which is the last argument of the op):
                Integer r = registerValueForByte(args.get(args.size()-1).registerOrFlagName);
                if (r == null) {
                    config.error("Unable to convert register name to value " + this);
                    return null;
                }
                data.add(baseByte+r);

            } else if (v[1].equals("+8*p") ||
                       v[1].equals("+8*q")) {
                // register (which is the last argument of the op):
                Integer r = registerValueForByte(args.get(args.size()-1).registerOrFlagName);
                if (r == null) {
                    config.error("Unable to convert register name to value " + this);
                    return null;
                }
                data.add(baseByte+8*r);
                
            } else if (v[1].equals("+8*b")) {
                Integer b = args.get(0).evaluateToInteger(s, code, true);
                if (b == null) {
                    config.error("Unable to convert bit to value " + this);
                }
                data.add(baseByte+8*(b&0x07));
                
            } else if (v[1].equals("+8*b+r")) {
                Integer b = args.get(0).evaluateToInteger(s, code, true);
                Integer r = registerValueForByte(args.get(1).registerOrFlagName);
                if (b == null || r == null) {
                    config.error("Unable to convert bit or register name to value " + this);
                }
                data.add(baseByte+8*(b&0x07)+r);
                
            } else {
                config.error("Unable to convert " + this + " to bytes! Unsupported byte modifier " + v[1]);
                return null;
            }
        }
        return data;
    }
    
    private Integer registerValueForByte(String register)
    {
        switch(register.toLowerCase()) {
            case "a":
                return 7;
            case "b":
                return 0;
            case "c":
                return 1;
            case "d":
                return 2;
            case "e":
                return 3;
            case "h":
            case "ixh":
            case "iyh":
                return 4;
            case "l":
            case "ixl":
            case "iyl":
                return 5;
            default:
                return null;
        }    
    }
    
    
    public boolean labelInRange(SourceStatement s, CodeBase code)
    {
        if (!isJump()) return true;
        
        int idx = spec.jumpLabelArgument();
        if (spec.args.get(idx).relativeLabelAllowed) {
            Expression target = args.get(idx);
            Integer endAddress = target.evaluateToInteger(s, code, true);
            if (endAddress == null) return false;
            Integer startAddress = s.getAddress(code);
            if (startAddress == null) return false;
            int diff = endAddress - startAddress;
            if (diff < -126 || diff > 130) return false;                
        }
        return true;
    }
}

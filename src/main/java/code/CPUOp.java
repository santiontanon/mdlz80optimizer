/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.util.ArrayList;
import java.util.List;
import parser.Tokenizer;
import sun.reflect.generics.tree.ByteSignature;

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
        
        for(String v[]:spec.bytesRepresentation) {
            int baseByte = Tokenizer.parseHex(v[0]);
            if (v[1].equals("")) {
                data.add(baseByte);
            } else if (v[1].equals("o")) {
                // jr/djnz offset:
                if (spec.isJump()) {
                    int base = s.getAddress(code) + spec.sizeInBytes;
                    int target = args.get(0).evaluateToInteger(s, code, true);
                    int offset = (target - base)&0xff;
                    data.add(offset);
                } else {
                    // ld IX/IY offet:
                    // ...
                    config.error("Unable to convert " + this + " to bytes! Unsupported byte modifier o for something different than a jump");
                    return null;
                }
            } else {
                config.error("Unable to convert " + this + " to bytes! Unsupported byte modifier " + v[1]);
                return null;
            }
        }
        return data;
    }
}

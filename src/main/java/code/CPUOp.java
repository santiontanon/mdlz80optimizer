/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;
import java.util.ArrayList;
import java.util.List;

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
        return toStringInternal(false, false, null, null, null);
    }
    
    
    String argString(Expression arg, boolean useOriginalNames, boolean mimicTargetDialect, CodeStatement s, CodeBase code, HTMLCodeStyle style)
    {
        if (arg == null) {
            return "null";
        }
        String argStr = arg.toStringInternal(false, useOriginalNames, mimicTargetDialect, s, code, style);
        if (config.fix_tniasm_parenthesisExpressionBug && 
            argStr.startsWith("(") &&
            arg.type != Expression.EXPRESSION_PARENTHESIS) {
            return "0 + (" + argStr + ")";
        }
        return argStr;
    }


    public String toStringInternal(boolean useOriginalNames, boolean mimicTargetDialect, CodeStatement s, CodeBase code, HTMLCodeStyle style)
    {
        String str = HTMLCodeStyle.renderStyledHTMLPiece(spec.opName, HTMLCodeStyle.TYPE_MNEMONIC, style);
        
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
                    str += "["+argString(args.get(i).args.get(0), useOriginalNames, mimicTargetDialect, s, code, style)+"]";
                } else {
                    str += argString(args.get(i), useOriginalNames, mimicTargetDialect, s, code, style);
                }
            } else {
                str += argString(args.get(i), useOriginalNames, mimicTargetDialect, s, code, style);
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

    
    public boolean dependsOnAnyFlag()
    {
        for(CPUOpDependency dep:inputDeps) {
            if (dep.flag != null) return true;
        }
        return false;
    }


    public boolean overwritesAllFlags()
    {
        int nFlagDeps = 0;
        for(CPUOpDependency dep:outputDeps) {
            if (dep.flag != null) nFlagDeps++;
        }
        return nFlagDeps >= 6;  // As there are 6 useful flags in the z80
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
        int labelArg = spec.jumpLabelArgument;
        if (labelArg == -1) return null;
        Expression targetLabel = args.get(labelArg);
        return targetLabel;
    }


    public SourceConstant getTargetJumpLabel(CodeBase code)
    {
        int labelArg = spec.jumpLabelArgument;
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
        return spec.isConditional;
    }
    
    
    public boolean writesToMemory()
    {
        return spec.outputMemoryStart != null;
    }
    
    
    /*
    This method attempts to retrieve the expression that represents the address
    in memory that an instruction writes to. If it returns "null", it does NOT
    mean that the instruction does not write to memory. It could mean that
    either the instruction does not write to memory, or that this method was
    not able to identify the expression.
    */
    public Expression getMemoryWriteExpression()
    {
        if (!writesToMemory()) return null;
        
        String opsThatWriteToFirst[] = {"ld",
                                        "inc", "Dec", "rl", "rlc", "rr", "rrc",
                                        "sla", "sli", "sra", "srl", "sll", "sl1"};
        for(String opName:opsThatWriteToFirst) {
            if (spec.opName.equalsIgnoreCase(opName)) {
                if (spec.args.get(0).wordConstantIndirectionAllowed ||
                    spec.args.get(0).regIndirection != null ||
                    spec.args.get(0).regOffsetIndirection != null) {
                    return args.get(0);
                }
            }
        }
        
        String opsThatWriteToSecond[] = {"res", "set"};
        for(String opName:opsThatWriteToSecond) {
            if (spec.opName.equalsIgnoreCase(opName)) {
                if (spec.args.get(1).wordConstantIndirectionAllowed ||
                    spec.args.get(1).regIndirection != null ||
                    spec.args.get(1).regOffsetIndirection != null) {
                    return args.get(1);
                }
            }
        }

        // push/pop (return null)
        // ex (sp),XXX (return null)
        // rst XXX (return null)
        // call XXX / ret / reti / retn (return null)
        // ini / ind / inir / indr (return null)
        // ldd / ldi / lddr / ldir (return null)
        return null;
    }
    
    
    public boolean readsFromMemory()
    {
        return spec.inputMemoryStart != null;        
    }

    
    public boolean isLd()
    {
        return spec.opName.equalsIgnoreCase("ld");
    }


    public boolean isLdToMemory()
    {
        if (!spec.opName.equalsIgnoreCase("ld")) return false;
        if (spec.args.get(0).wordConstantIndirectionAllowed ||
            spec.args.get(0).regIndirection != null ||
            spec.args.get(0).regOffsetIndirection != null) {
            return true;
        }
        return false;
    }
    

    public boolean isAdd()
    {
        return spec.isAdd;
    }

    
    public boolean isNop()
    {
        return spec.isNop;
    }

    
    public boolean isRet()
    {
        return spec.isRet;
    }
    
    
    public boolean isRst()
    {
        return spec.isRst;
    }
    
    
    public boolean mightJump()
    {
        return spec.mightJump;
    }


    public boolean isCall()
    {
        return spec.isCall;
    }

    
    public boolean isJump()
    {
        return spec.isJump;
    }
    

    public boolean isRelativeJump()
    {
        return spec.isRelativeJump;
    }

    
    public boolean isPush()
    {
        return spec.isPush;
    }


    public boolean isPop()
    {
        return spec.isPop;
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
        if (spec.isRst) return true;
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
    
    
    public boolean modifiesRegister(String register)
    {
        for(CPUOpDependency outDep:getOutputDependencies()) {
            if (outDep.register != null &&
                outDep.register.equalsIgnoreCase(register)) return true;
        }
        return false;
    }


    public boolean evaluateAllExpressions(CodeStatement s, CodeBase code, MDLConfig config)
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
    
    
    public static boolean offsetWithinJrRange(int diff)
    {
        if (diff < -128 || diff > 127) return false;
        return true;
    }


    public List<Integer> assembleToBytes(CodeStatement s, CodeBase code, MDLConfig config)
    {
        try {
            return assembleToBytes(s, code, false, config);
        } catch (Exception e) {
            config.error("Cannot convert " + this + " to bytes!");
            return null;
        }
    }    
    
    public List<Integer> assembleToBytes(CodeStatement s, CodeBase code, boolean silent, MDLConfig config)
    {
        List<Integer> data = new ArrayList<>();
        int nn_state = 0;
        
        for(int i=0;i<spec.bytesRepresentation.size();i++) {
            String v[] = spec.bytesRepresentation.get(i);
            int baseByte = spec.bytesRepresentationBaseByte.get(i);
            switch (v[1]) {
                case "":
                    data.add(baseByte);
                    break;
                case "o":
                    if (spec.isJump) {
                        // jr/djnz offset:
                        if (s == null) {
                            if (!silent) config.error("Trying to convert " + this + " to bytes without specifying a CodeStatement.");
                            return null;
                        }
                        if (args == null || args.isEmpty() || args.get(args.size()-1) == null) {
                            if (!silent) config.error("Unable to convert " + this + " to bytes! Could not find the target label for the jump");
                            return null;
                        }
                        Integer target = args.get(args.size()-1).evaluateToInteger(s, code, true);
                        if (target == null) {
                            if (!silent) config.error("Unable to resolve '" + args.get(args.size()-1) + "' in " + s.sl);
                            return null;
                        }
                        
                        int base = s.getAddress(code) + spec.sizeInBytes;
                        int offset = (target - base);
                        
                        // check if it's within jr range!:
                        if (!offsetWithinJrRange(offset)) {
                            if (!silent) config.error("Jump offset out of range: " + offset + " in " + s.sl);
                            return null;
                        }
                        
                        data.add(offset & 0xff);
                    } else {
                        // ld IX/IY offet:
                        int arg_idx = -1;
                        for(int j = 0;j<args.size();j++) {
                            if (spec.args.get(j).regOffsetIndirection != null) {
                                arg_idx = j;
                            }
                        }
                        if (arg_idx == -1) {
                            if (!silent) config.error("Unable to convert " + this + " to bytes! Could not find argument with an indirection with offset");
                            return null;
                        }
                        Integer o = null;
                        if (args.get(arg_idx).type == Expression.EXPRESSION_PARENTHESIS) {
                            Expression arg = args.get(arg_idx).args.get(0);
                            switch (arg.type) {
                                case Expression.EXPRESSION_SUM:
                                    o = arg.args.get(1).evaluateToInteger(s, code, true);
                                    break;
                                case Expression.EXPRESSION_SUB:
                                {
                                    o = arg.args.get(1).evaluateToInteger(s, code, true);
                                    if (o != null) o = -o;
                                    break;
                                }
                                case Expression.EXPRESSION_REGISTER_OR_FLAG:
                                    o = 0;
                                    break;
                            }
                        }
                        if (o == null) {
                            if (!silent) config.error("Unable to convert " + this + " to bytes! Could not find the offset");
                            return null;
                        }
                        data.add(o);
                    }   break;
                case "n":
                    // 8 bit argument
                    Integer n = null;
                    for(Expression arg:args) {
                        String undefined = arg.findUndefinedSymbol(code);
                        if (undefined != null) {
                            if (!silent) config.error("Undefined symbol \"" + undefined + "\"" + (s!=null ? " in " + s.sl.fileNameLineString():" in synthetic op " + this));
                            return null;
                        }
                        if (arg.evaluatesToIntegerConstant()) {
                            if (n != null) {
                                if (!silent) config.error("Unable to convert " + this + " to bytes! two numeric constants in one op!");
                                return null;
                            }
                            n = arg.evaluateToInteger(s, code, true);
                        }
                    }   if (n == null) {
                        if (!silent) config.error("Unable to convert " + this + " to bytes! no 8bit numeric constants in the op!");
                        return null;
                    }   data.add(n & 0xff);
                    break;
                case "n1":
                case "n2":
                    // 8 bit argument
                    Integer n1 = null;
                    Integer n2 = null;
                    for(Expression arg:args) {
                        String undefined = arg.findUndefinedSymbol(code);
                        if (undefined != null) {
                            if (!silent) config.error("Undefined symbol \"" + undefined + "\"" + (s!=null ? " in " + s.sl.fileNameLineString():" in synthetic op " + this));
                            return null;
                        }
                        if (arg.evaluatesToIntegerConstant()) {
                            if (n1 != null && n2 != null) {
                                if (!silent) config.error("Unable to convert " + this + " to bytes! more than two numeric constants in one op!");
                                return null;
                            }
                            if (n1 == null) {
                                n1 = arg.evaluateToInteger(s, code, true);
                            } else {
                                n2 = arg.evaluateToInteger(s, code, true);
                            }
                        }
                    }   if (v[1].equals("n1")) {
                        if (n1 == null) {
                            if (!silent) config.error("Unable to convert " + this + " to bytes! no 8bit numeric constants in the op!");
                            return null;
                        }
                        data.add(n1 & 0xff);
                    } else {
                        if (n2 == null) {
                            if (!silent) config.error("Unable to convert " + this + " to bytes! only one 8bit numeric constant in the op!");
                            return null;
                        }
                        data.add(n2 & 0xff);
                    }   break;
                case "nn":
                case "mm":
                    // 16 bit argument
                    Integer nn = null;
                    for(Expression arg:args) {
                        String undefined = arg.findUndefinedSymbol(code);
                        if (undefined != null) {
                            if (!silent) config.error("Undefined symbol \"" + undefined + "\"" + (s!=null ? " in " + s.sl.fileNameLineString():" in synthetic op " + this));
                            return null;
                        }
                        if (arg.evaluatesToIntegerConstant()) {
                            if (nn != null) {
                                if (!silent) config.error("Unable to convert " + this + " to bytes! two numeric constants in one op!");
                                return null;
                            }
                            nn = arg.evaluateToInteger(s, code, true);
                            if (nn == null) {
                                if (!silent) config.error("Unable to convert " + this + " to bytes! Cannot evaluate " + arg);
                                return null;
                            }
                        }
                    }   if (nn == null) {
                        if (!silent) config.error("Unable to convert " + this + " to bytes! no 16bit numeric constants in the op! Arguments: " + args);
                        return null;
                    }   if (v[1].equals("nn")) {
                        if (nn_state == 0) {
                            data.add(nn & 0xff);
                            nn_state = 1;
                        } else {
                            data.add((nn >> 8) & 0xff);
                        }
                    } else {
                        // big endian:
                        if (nn_state == 0) {
                            data.add((nn >> 8) & 0xff);
                            nn_state = 1;
                        } else {
                            data.add(nn & 0xff);
                        }
                    }   break;
                case "+0":
                    data.add(baseByte+0);
                    break;
                case "+1":
                    data.add(baseByte+1);
                    break;
                case "+2":
                    data.add(baseByte+2);
                    break;
                case "+3":
                    data.add(baseByte+3);
                    break;
                case "+4":
                    data.add(baseByte+4);
                    break;
                case "+5":
                    data.add(baseByte+5);
                    break;
                case "+6":
                    data.add(baseByte+6);
                    break;
                case "+7":
                    data.add(baseByte+7);
                    break;
                case "+r":
                case "+p":
                case "+q":
                    {
                        // register (which is the last argument of the op):
                        Integer r = registerValueForByte(args.get(args.size()-1).registerOrFlagName);
                        if (r == null) {
                            if (!silent) config.error("Unable to convert register name to value " + this);
                            return null;
                        }       data.add(baseByte+r);
                        break;
                    }
                case "+8*p":
                case "+8*q":
                    {
                        // register (which is the last argument of the op):
                        Integer r = registerValueForByte(args.get(args.size()-1).registerOrFlagName);
                        if (r == null) {
                            if (!silent) config.error("Unable to convert register name to value " + this);
                            return null;
                        }       data.add(baseByte+8*r);
                        break;
                    }
                case "+8*b":
                    {
                        Integer b = args.get(0).evaluateToInteger(s, code, true);
                        if (b == null) {
                            if (!silent) config.error("Unable to convert bit to value " + this);
                        }       data.add(baseByte+8*(b&0x07));
                        break;
                    }
                case "+8*b+r":
                    {
                        Integer b = args.get(0).evaluateToInteger(s, code, true);
                        Integer r = registerValueForByte(args.get(1).registerOrFlagName);
                        if (b == null || r == null) {
                            if (!silent) config.error("Unable to convert bit or register name to value " + this);
                        }       data.add(baseByte+8*(b&0x07)+r);
                        break;
                    }
                case "+8*r":
                    {
                        Integer r = registerValueForByte(
                                args.get(0).registerOrFlagName != null ?
                                        args.get(0).registerOrFlagName :
                                        args.get(1).registerOrFlagName);
                        if (r == null) {
                            if (!silent) config.error("Unable to convert bit or register name to value " + this);
                        }       data.add(baseByte+8*r);
                        break;
                    }
                default:
                    if (!silent) config.error("Unable to convert " + this + " to bytes! Unsupported byte modifier " + v[1]);
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
    
    
    public boolean labelInRange(CodeStatement s, CodeBase code)
    {
        if (!isJump()) return true;
        
        int idx = spec.jumpLabelArgument;
        if (spec.args.get(idx).relativeLabelAllowed) {
            Expression target = args.get(idx);
            Integer endAddress = target.evaluateToInteger(s, code, true);
            if (endAddress == null) {
                return false;
            }
            Integer startAddress = s.getAddressAfter(code);
            if (startAddress == null) {
                return false;
            }
            int diff = endAddress - startAddress;
            if (!offsetWithinJrRange(diff)) {
                return false;
            }   
        }
        return true;
    }
    
    
    public void setConfig(MDLConfig newConfig)
    {
        config = newConfig;
        for(Expression arg:args) {
            arg.setConfig(newConfig);
        }
    }
    
}

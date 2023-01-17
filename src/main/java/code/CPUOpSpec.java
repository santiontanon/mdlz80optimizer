/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import cl.MDLConfig;

import java.util.ArrayList;
import java.util.List;

public class CPUOpSpec {
    MDLConfig config;
    public String opName;
    public Expression opNameExp;
    public List<CPUOpSpecArg> args = new ArrayList<>();
    public int sizeInBytes;
    public int times[];
    public String byteRepresentationRaw = null;    // this is useful to check if two instructions are the same
    public List<String[]> bytesRepresentation; // each element is a byte, and the array has 2 elements: the byte, and the modifier
    public List<Integer> bytesRepresentationBaseByte;
    
    public boolean official;
    public CPUOpSpec officialEquivalent = null;
    public List<List<String>> fakeInstructionEquivalent = null;

    public boolean isJpRegWithParenthesis = false;

    // Dependency data:
    public List<String> inputRegs, inputFlags;
    public String inputPort = null, inputMemoryStart = null, inputMemoryEnd = null;
    public List<String> outputRegs, outputFlags;
    public String outputPort = null, outputMemoryStart = null, outputMemoryEnd = null;
    
    // Precompute instruction types:
    public boolean isConditional = false, isRet = false, isRst = false, isCall = false,
                   isJump = false, isRelativeJump = false, isPush = false, isPop = false,
                   mightJump = false;
    public int jumpLabelArgument = -1;

    
    public CPUOpSpec(String a_opName, int a_size, int a_times[], String a_byteRepresentation, boolean a_official, MDLConfig a_config)
    {
        opName = a_opName;
        opNameExp = Expression.symbolExpressionInternalWithoutChecks(opName, a_config);
        sizeInBytes = a_size;
        times = a_times;
        byteRepresentationRaw = a_byteRepresentation;
        official = a_official;
        config = a_config;
        
        bytesRepresentation = new ArrayList<>();
        bytesRepresentationBaseByte = new ArrayList<>();
        for(String byteString:byteRepresentationRaw.split(" ")) {
            List<String> tokens = config.tokenizer.tokenize(byteString);
            if (tokens.get(0).equals("o") ||
                tokens.get(0).equals("n") ||
                tokens.get(0).equals("n1") ||
                tokens.get(0).equals("n2") ||
                tokens.get(0).equals("nn") ||
                tokens.get(0).equals("mm")) {
                bytesRepresentation.add(new String[]{"0", byteString});
                bytesRepresentationBaseByte.add(0);
            } else {
                bytesRepresentation.add(new String[]{tokens.get(0), byteString.substring(tokens.get(0).length())});
                bytesRepresentationBaseByte.add(config.tokenizer.parseHex(tokens.get(0)));
            }
        }
        assert(sizeInBytes == bytesRepresentation.size());
    }
    
    
    public void precomputeInstructionTypes()
    {
        // precompute instruction types:
        if (inputFlags!= null && !inputFlags.isEmpty() &&
            (opName.equalsIgnoreCase("cp") ||
             opName.equalsIgnoreCase("call") ||
             opName.equalsIgnoreCase("ret") ||
             opName.equalsIgnoreCase("jp") ||
             opName.equalsIgnoreCase("jr"))) {
            isConditional = true;
        }
        if (opName.equalsIgnoreCase("djnz")) isConditional = true;
        if (opName.equalsIgnoreCase("call") ||
            opName.equalsIgnoreCase("jp") ||
            opName.equalsIgnoreCase("jr") ||
            opName.equalsIgnoreCase("djnz") ||
            opName.equalsIgnoreCase("rst")) {
            jumpLabelArgument = args.size()-1;
        }
        if (opName.equalsIgnoreCase("ret")) isRet = true;
        if (opName.equalsIgnoreCase("reti")) isRet = true;
        if (opName.equalsIgnoreCase("retn")) isRet = true;
        if (opName.equalsIgnoreCase("rst")) isRst = true;
        if (opName.equalsIgnoreCase("call")) isCall = true;
        if (opName.equalsIgnoreCase("jp")) isJump = true;
        if (opName.equalsIgnoreCase("jr")) isJump = true;
        if (opName.equalsIgnoreCase("djnz")) isJump = true;
        if (opName.equalsIgnoreCase("jr")) isRelativeJump = true;
        if (opName.equalsIgnoreCase("djnz")) isRelativeJump = true;
        if (opName.equalsIgnoreCase("push")) isPush = true;
        if (opName.equalsIgnoreCase("pop")) isPop = true;
        if (isRet || jumpLabelArgument != -1) mightJump = true;
    }
    
    
    @Override
    public String toString()
    {
        return opName + " " + args;
    }
    
    
    public boolean searchOfficialEquivalent(List<CPUOpSpec> specs)
    {
        if (official) return true;
        for(CPUOpSpec spec:specs) {
            if (spec == this) continue;
            if (spec.byteRepresentationRaw.equals(byteRepresentationRaw)) {
                officialEquivalent = spec;
                return true;
            }
        }
        return false;
    }
    
    
    public String getName()
    {
        return opName;
    }


    public void addArgSpec(CPUOpSpecArg arg) {
        args.add(arg);
    }


    public String timeString()
    {
        String str = "";
        for(int time:times) {
            if (str.isEmpty()) {
                str += time;
            } else {
                str += "/" + time;
            }
        }
        return str;
    }


    public boolean isPrimitiveReg(String regName)
    {
        // by convention, regnames are all in uppercase letters:
        if (regName.toUpperCase().equals(regName)) return true;
        return false;
    }


    public boolean regMatch(String pattern, String regName)
    {
        if (pattern.equals("r")) {
            if (regName.equalsIgnoreCase("a") ||
                regName.equalsIgnoreCase("b") ||
                regName.equalsIgnoreCase("c") ||
                regName.equalsIgnoreCase("d") ||
                regName.equalsIgnoreCase("e") ||
                regName.equalsIgnoreCase("h") ||
                regName.equalsIgnoreCase("l")) return true;
        } else if (pattern.equals("p")) {
            if (regName.equalsIgnoreCase("a") ||
                regName.equalsIgnoreCase("b") ||
                regName.equalsIgnoreCase("c") ||
                regName.equalsIgnoreCase("d") ||
                regName.equalsIgnoreCase("e") ||
                regName.equalsIgnoreCase("IXH") ||
                regName.equalsIgnoreCase("IXL")) return true;
        } else if (pattern.equals("q")) {
            if (regName.equalsIgnoreCase("a") ||
                regName.equalsIgnoreCase("b") ||
                regName.equalsIgnoreCase("c") ||
                regName.equalsIgnoreCase("d") ||
                regName.equalsIgnoreCase("e") ||
                regName.equalsIgnoreCase("IYH") ||
                regName.equalsIgnoreCase("IYL")) return true;
        } else if (pattern.equals("IXp")) {
            if (regName.equalsIgnoreCase("IXH") ||
                regName.equalsIgnoreCase("IXL")) return true;
        } else if (pattern.equals("IYq")) {
            if (regName.equalsIgnoreCase("IYH") ||
                regName.equalsIgnoreCase("IYL")) return true;
        } else {
            return pattern.equalsIgnoreCase(regName);
        }
        return false;
    }


    public List<CPUOpDependency> getInputDependencies(List<Expression> opArgs)
    {
        List<CPUOpDependency> deps = new ArrayList<>();

        for(String reg:inputRegs) {
            if (isPrimitiveReg(reg)) {
                deps.add(new CPUOpDependency(reg, null, null, null, null));
            } else {
                // we need to find which reg in particular is used in this op:
                for(int i = 0;i<args.size();i++) {
                    CPUOpSpecArg specArg = args.get(i);
                    if (specArg.reg != null && specArg.reg.equals(reg)) {
                        Expression opArg = opArgs.get(i);
                        if (opArg.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                            deps.add(new CPUOpDependency(opArg.registerOrFlagName.toUpperCase(), null, null, null, null));
                        } else {
                            config.error("getInputDependencies Register expression is not of type EXPRESSION_REGISTER_OR_FLAG");
                            return null;
                        }
                    }
                }
            }
        }
        for(String flag:inputFlags) {
            deps.add(new CPUOpDependency(null, flag, null, null, null));
        }
        if (inputPort != null) {
            deps.add(new CPUOpDependency(null, null, inputPort, null, null));
        }
        if (inputMemoryStart != null) {
            deps.add(new CPUOpDependency(null, null, null, inputMemoryStart, inputMemoryEnd));
        }

        return deps;
    }


    public List<CPUOpDependency> getOutputDependencies(List<Expression> opArgs)
    {
        List<CPUOpDependency> deps = new ArrayList<>();

        for(String reg:outputRegs) {
            if (isPrimitiveReg(reg)) {
                deps.add(new CPUOpDependency(reg, null, null, null, null));
            } else {
                // we need to find which reg in particular is used in this op:
                for(int i = 0;i<args.size();i++) {
                    CPUOpSpecArg specArg = args.get(i);
                    if (specArg.reg != null && specArg.reg.equals(reg)) {
                        Expression opArg = opArgs.get(i);
                        if (opArg.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
                            deps.add(new CPUOpDependency(opArg.registerOrFlagName.toUpperCase(), null, null, null, null));
                        } else {
                            config.error("getOutputDependencies Register expression is not of type EXPRESSION_REGISTER_OR_FLAG");
                            return null;
                        }
                    }
                }
            }
        }
        for(String flag:outputFlags) {
            deps.add(new CPUOpDependency(null, flag, null, null, null));
        }
        if (outputPort != null) {
            deps.add(new CPUOpDependency(null, null, outputPort, null, null));
        }
        if (outputMemoryStart != null) {
            deps.add(new CPUOpDependency(null, null, null, outputMemoryStart, outputMemoryEnd));
        }

        return deps;
    }

    
    public CPUOp tryToDisassemble(List<Integer> data, int offset, CodeBase code) {
        if (sizeInBytes > data.size() - offset) {
            // there isn't enough data!
            return null;
        }
        
        CPUOp op = null;
        Integer previousNN = null;
        Integer previousMM = null;

        for(int i = 0;i<bytesRepresentation.size();i++) {
            int v = data.get(i+offset);
            String specV[] = bytesRepresentation.get(i);
            int baseByte = bytesRepresentationBaseByte.get(i);
            switch (specV[1]) {            
                case "":
                    if (v != baseByte) return null;
                    if (op == null) op = disassembleInstantiateOp(code);
                    break;

                case "o":
                    if (op == null) return null;
                    if (isJump) {
                        // relative jump offset:
                        int jumpOffset = v + 2;
                        if (jumpOffset >= 130) {
                            jumpOffset -= 256;
                            if (!disassembleAddArg(op, Expression.operatorExpression(Expression.EXPRESSION_SUB,
                                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, null, code, config),
                                    Expression.constantExpression(-jumpOffset, config), config), true, false)) return null;
                        } else {
                            if (!disassembleAddArg(op, Expression.operatorExpression(Expression.EXPRESSION_SUM,
                                    Expression.symbolExpression(CodeBase.CURRENT_ADDRESS, null, code, config),
                                    Expression.constantExpression(jumpOffset, config), config), true, false)) return null;
                        }
                    } else {
                        // indexing offset:
                        if (!disassembleAddArg(op, Expression.constantExpression(v, config), false, true)) return null;
                    }
                    break;
                    
                case "n":
                case "n1":
                case "n2":
                    if (op == null) return null;
                    if (!disassembleAddArg(op, Expression.constantExpression(v, config), true, true)) return null;
                    break;
                case "nn":
                    // 16 bit argument
                    if (op == null) return null;
                    if (previousNN == null) {
                        previousNN = v;
                    } else {
                        if (!disassembleAddArg(op, Expression.constantExpression(previousNN + v*256, config), true, true)) return null;
                        previousNN = null;
                    }
                    break;
                case "mm":
                    // 16 bit argument
                    if (op == null) return null;
                    if (previousMM == null) {
                        previousMM = v;
                    } else {
                        if (!disassembleAddArg(op, Expression.constantExpression(previousMM + v*256, config), true, true)) return null;
                        previousMM = null;
                    }
                    break;
                case "+0":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 0) return null;
                    break;
                case "+1":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 1) return null;
                    break;
                case "+2":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 2) return null;
                    break;
                case "+3":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 3) return null;
                    break;
                case "+4":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 4) return null;
                    break;
                case "+5":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 5) return null;
                    break;
                case "+7":
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (v != baseByte + 7) return null;
                    break;
                case "+r":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    String reg = registerForValue(v - baseByte);
                    if (reg == null) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                case "+p":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    String reg = registerForValueP(v - baseByte);
                    if (reg == null) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                case "+q":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    String reg = registerForValueQ(v - baseByte);
                    if (reg == null) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                case "+8*p":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (((v - baseByte)%8) != 0) return null;
                    String reg = registerForValueP((v - baseByte)/8);
                    if (reg == null) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                case "+8*q":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (((v - baseByte)%8) != 0) return null;
                    String reg = registerForValueQ((v - baseByte)/8);
                    if (reg == null) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                case "+8*b":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (((v - baseByte)%8) != 0) return null;
                    int b = (v - baseByte)/8;
                    if (b<0 || b>=8) return null;
                    if (!disassembleAddArg(op, Expression.constantExpression(b, config), true, false)) return null;
                    break;
                }
                case "+8*b+r":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    int diff = (v - baseByte);
                    String reg = registerForValue(diff % 8);
                    if (reg == null) return null;
                    int b = diff/8;
                    if (b<0 || b>=8) return null;
                    if (!disassembleAddArg(op, Expression.constantExpression(b, config), true, false)) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                case "+8*r":
                {
                    if (op == null) op = disassembleInstantiateOp(code);
                    if (((v - baseByte)%8) != 0) return null;
                    String reg = registerForValue((v - baseByte)/8);
                    if (reg == null) return null;
                    if (!disassembleAddArg(op, Expression.symbolExpression(reg, null, code, config), true, false)) return null;
                    break;
                }
                default:
                    return null;            
            }
        }
        if (op != null && op.args.size() == args.size()) {
            if (disassembleAddArg(op, null, true, true)) {
                config.error("" + op);
                return null;
            }
            return op;
        }
        return null;
    }
    
    
    public CPUOp disassembleInstantiateOp(CodeBase code) {
        CPUOp op = new CPUOp(this, new ArrayList<>(), config);

        // Add all the arguments that do not depend on additional bytes:
        for(CPUOpSpecArg specArg:args) {                         
            if (specArg.reg != null) {
                if (CodeBase.isRegister(specArg.reg) && specArg.reg.toUpperCase().equals(specArg.reg)) {
                    op.args.add(Expression.symbolExpression(specArg.reg, null, code, config));
                } else {
                    op.args.add(null);
                }
            } else if (specArg.regIndirection != null) {
                op.args.add(Expression.parenthesisExpression(
                        Expression.symbolExpression(specArg.regIndirection, null, code, config),
                        "(", config));
            } else if (specArg.regOffsetIndirection != null) {
                op.args.add(Expression.parenthesisExpression(
                        Expression.operatorExpression(Expression.EXPRESSION_SUM, 
                                Expression.symbolExpression(specArg.regOffsetIndirection, null, code, config),
                                null,
                                config),
                        "(", config));
            } else if (specArg.condition != null) {
                op.args.add(Expression.symbolExpression(specArg.condition, null, code, config));
            } else if (specArg.byteConstantIndirectionAllowed ||
                       specArg.wordConstantIndirectionAllowed) {
                op.args.add(Expression.parenthesisExpression(null, "(", config));
            } else if (specArg.byteConstantAllowed ||
                       specArg.wordConstantAllowed) {
                if (specArg.min != null && specArg.min.equals(specArg.max)) {
                    op.args.add(Expression.constantExpression(specArg.min, config));
                } else {
                    op.args.add(null);
                }
            } else {                
                op.args.add(null);
            }
        }
        return op;
    }
    
        
    private String registerForValue(int value)
    {
        String registers[] = {"b", "c", "d", "e", "h", "l", null, "a"};
        if (value >= 0 && value < registers.length) {
            return registers[value];
        }
        return null;
    }
    

    private String registerForValueP(int value)
    {
        String registers[] = {null, null, null, null, "ixh", "ixl", null, null};
        if (value >= 0 && value < registers.length) {
            return registers[value];
        }
        return null;
    }

    
    private String registerForValueQ(int value)
    {
        String registers[] = {null, null, null, null, "iyh", "iyl", null, null};
        if (value >= 0 && value < registers.length) {
            return registers[value];
        }
        return null;
    }

    
    /*
    - useAsItsOwnExpression: set this to "true" if "exp" can be an argument of
                             the op by itself (for example, an indirection
                             offset cannot, as it is always inside some
                             expression, like (ix+o)
    - lookInsideExpressions: set this to "true" if "exp" can be placed inside
                             of an expression.
    */
    private boolean disassembleAddArg(CPUOp op, Expression exp, boolean useAsItsOwnExpression, boolean lookInsideExpressions)
    {
        for(int i = 0;i<op.args.size();i++) {
            if (op.args.get(i) == null && useAsItsOwnExpression) {
                op.args.set(i, exp);
                return true;
            } else if (op.args.get(i) != null && lookInsideExpressions) {
                if (disassembleAddArg(op.args.get(i), exp)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    
    private boolean disassembleAddArg(Expression argExp, Expression exp)
    {
        if (argExp.args == null) return false;
        for(int i = 0;i<argExp.args.size();i++) {
            if (argExp.args.get(i) == null) {
                argExp.args.set(i, exp);
                return true;
            } else {
                if (disassembleAddArg(argExp.args.get(i), exp)) {
                    return true;
                }
            }
        }
        return false;
    }
        
}

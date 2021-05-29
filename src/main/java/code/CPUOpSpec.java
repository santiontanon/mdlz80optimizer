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
        opNameExp = Expression.symbolExpressionInternal2(opName, a_config);
        sizeInBytes = a_size;
        times = a_times;
        byteRepresentationRaw = a_byteRepresentation;
        official = a_official;
        config = a_config;
        
        bytesRepresentation = new ArrayList<>();
        for(String byteString:byteRepresentationRaw.split(" ")) {
            List<String> tokens = config.tokenizer.tokenize(byteString);
            if (tokens.get(0).equals("o") ||
                tokens.get(0).equals("n") ||
                tokens.get(0).equals("nn")) {
                bytesRepresentation.add(new String[]{"0", byteString});
            } else {
                bytesRepresentation.add(new String[]{tokens.get(0), byteString.substring(tokens.get(0).length())});
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
            opName.equalsIgnoreCase("djnz")) {
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

    /*
    public boolean isConditional()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (inputFlags!= null && !inputFlags.isEmpty() &&
            (opName.equalsIgnoreCase("cp") ||
             opName.equalsIgnoreCase("call") ||
             opName.equalsIgnoreCase("ret") ||
             opName.equalsIgnoreCase("jp") ||
             opName.equalsIgnoreCase("jr"))) {
            return true;
        }
        if (opName.equalsIgnoreCase("djnz")) return true;
        return false;
    }


    public int jumpLabelArgument()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("call") ||
            opName.equalsIgnoreCase("jp") ||
            opName.equalsIgnoreCase("jr") ||
            opName.equalsIgnoreCase("djnz")) {
            return args.size()-1;
        }
        return -1;
    }


    public boolean isRet()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("ret")) return true;
        if (opName.equalsIgnoreCase("reti")) return true;
        if (opName.equalsIgnoreCase("retn")) return true;
        return false;
    }


    public boolean isRst()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("rst")) return true;
        return false;
    }

    
    public boolean isCall()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("call")) return true;
        return false;
    }

    
    public boolean isJump()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("jp")) return true;
        if (opName.equalsIgnoreCase("jr")) return true;
        if (opName.equalsIgnoreCase("djnz")) return true;
        return false;
    }
    
    
    public boolean isRelativeJump()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("jr")) return true;
        if (opName.equalsIgnoreCase("djnz")) return true;
        return false;
    }
    

    public boolean isPush()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("push")) return true;
        return false;
    }
    
    
    public boolean isPop()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (opName.equalsIgnoreCase("pop")) return true;
        return false;
    }


    public boolean mightJump()
    {
        // TODO(santi@): move this info to the CPU definition file
        if (isRet()) return true;
        if (jumpLabelArgument() != -1) return true;
        return false;
    }
    */
        
}

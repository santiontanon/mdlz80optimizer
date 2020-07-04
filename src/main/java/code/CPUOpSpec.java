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
    public List<CPUOpSpecArg> args = new ArrayList<>();
    int sizeInBytes;
    int times[];
    String byteRepresentation = null;   // even if MDL does not compile,
                                        // this is useful to check if two instructions are the same
    public boolean official;
    public CPUOpSpec officialEquivalent = null;

    public boolean isJpRegWithParenthesis = false;
    
    // Dependency data:
    public List<String> inputRegs, inputFlags;
    public String inputPort = null, inputMemoryStart = null, inputMemoryEnd = null;
    public List<String> outputRegs, outputFlags;
    public String outputPort = null, outputMemoryStart = null, outputMemoryEnd = null;
    
    
    public CPUOpSpec(String a_opName, int a_size, int a_times[], String a_byteRepresentation, boolean a_official, MDLConfig a_config)
    {
        opName = a_opName;
        sizeInBytes = a_size;
        times = a_times;
        byteRepresentation = a_byteRepresentation;
        official = a_official;
        config = a_config;
    }
    
    
    public boolean searchOfficialEquivalent(List<CPUOpSpec> specs)
    {
        if (official) return true;
        for(CPUOpSpec spec:specs) {
            if (spec == this) continue;
            if (spec.byteRepresentation.equals(byteRepresentation)) {
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
                regName.equalsIgnoreCase("IXh") ||
                regName.equalsIgnoreCase("IXl")) return true;
        } else if (pattern.equals("q")) {
            if (regName.equalsIgnoreCase("a") ||
                regName.equalsIgnoreCase("b") ||
                regName.equalsIgnoreCase("c") ||
                regName.equalsIgnoreCase("d") ||
                regName.equalsIgnoreCase("e") ||
                regName.equalsIgnoreCase("IYh") ||
                regName.equalsIgnoreCase("IYl")) return true;
        } else if (pattern.equals("IXp")) {
            if (regName.equalsIgnoreCase("IXh") ||
                regName.equalsIgnoreCase("IXl")) return true;
        } else if (pattern.equals("IYq")) {
            if (regName.equalsIgnoreCase("IYh") ||
                regName.equalsIgnoreCase("IYl")) return true;            
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
    public List<CPUOpDependency> checkDependencies(List<Expression> opArgs, List<CPUOpDependency> inputDeps)
    {
        List<CPUOpDependency> deps = new ArrayList<>();
        List<CPUOpDependency> outputDeps = getOutputDependencies(opArgs);
        
        for(CPUOpDependency d1:inputDeps) {
            for(CPUOpDependency d2:outputDeps) {
                if (d1.match(d2)) {
                    deps.add(d1);
                    break;
                }
            }
        }
        
        return deps;
    }
    */
    
    
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
            // opName.equalsIgnoreCase("ret") ||
            opName.equalsIgnoreCase("jp") ||
            opName.equalsIgnoreCase("jr") ||
            opName.equalsIgnoreCase("djnz")) {
            return args.size()-1;
        }
        return -1;
    }
    
    
    public boolean isRet()
    {
        if (opName.equalsIgnoreCase("ret")) return true;
        return false;
    }
            
}

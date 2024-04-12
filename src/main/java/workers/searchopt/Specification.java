/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOpDependency;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.tuple.Pair;
import util.microprocessor.Z80.CPUConstants;

/**
 *
 * @author santi
 */
public class Specification {
    int numberOfRandomSolutionChecks = 10000;
    int codeStartAddress = 0x4000;
    int maxSimulationTime = 256;
    int maxSizeInBytes = 256;
    int maxOps = 4;
    public int searchType = SearchBasedOptimizer.SEARCH_ID_OPS_SAFE;
    int searchTimeCalculation = SearchBasedOptimizer.SEARCH_TIME_AVERAGE;
    
    // instruction set:
    public List<String> allowedOps = new ArrayList<>();
    public List<String> allowedRegisters = new ArrayList<>();
    public boolean allowRamUse = false;
    public boolean allowIO = false;
    public boolean allowGhostRegisters = false;
    public boolean allowLoops = false;
    public int maxBytesAlloedToJumpAhead = 5;
    
    public List<Integer> allowed8bitConstants = new ArrayList<>();
    public List<Integer> allowed16bitConstants = new ArrayList<>();
    public List<Integer> allowedOffsetConstants = new ArrayList<>();
    public List<Pair<Integer, Expression>> allowedJrTargets = new ArrayList<>();
    public List<Pair<Integer, Expression>> allowedJpTargets = new ArrayList<>();
    
    public List<InputParameter> parameters = new ArrayList<>();
    public List<SpecificationExpression> startState = new ArrayList<>();
    public List<SpecificationExpression> goalState = new ArrayList<>();
    
    // precomputed, so that we don't waste time during search evaluating
    // expressions:
    public PrecomputedTestCaseGenerator testCaseGenerator = null;
    public PrecomputedTestCase precomputedTestCases[] = null;
    
    
    boolean initialDependencies[] = null;
    boolean goalDependencies[] = null;
    int goalDependencyIndexes[] = null;
    boolean goalDependenciesSatisfiedFromTheStart[] = null;
    
    
    public Specification()
    {
        // By default, we only allow "0" as a constant:
        allowed8bitConstants.add(0);
        allowed16bitConstants.add(0);
        allowedOffsetConstants.add(0);
        
        allowedOps.add("and");
        allowedOps.add("or");
        allowedOps.add("xor");
        
        allowedOps.add("inc");
        allowedOps.add("dec");
        
        allowedOps.add("add");
        allowedOps.add("adc");
        allowedOps.add("sub");
        allowedOps.add("sbc");
        
        allowedOps.add("ld");
        
        allowedOps.add("rlc");
        allowedOps.add("rl");
        allowedOps.add("rrc");
        allowedOps.add("rr");
        allowedOps.add("rlca");
        allowedOps.add("rla");
        allowedOps.add("rrca");
        allowedOps.add("rra");
        
        allowedOps.add("sla");
        allowedOps.add("sra");
        allowedOps.add("srl");
        allowedOps.add("sli");

        allowedOps.add("cpl");
        allowedOps.add("neg");

        allowedOps.add("bit");
        allowedOps.add("set");
        allowedOps.add("res");

        allowedOps.add("ccf");
        allowedOps.add("scf");

        allowedOps.add("cp");
        
        allowedOps.add("jp");
        allowedOps.add("jr");
        allowedOps.add("djnz");
        
        allowedOps.add("ex");        
        
        allowedRegisters.add("a");
        allowedRegisters.add("af");
        allowedRegisters.add("b");
        allowedRegisters.add("c");
        allowedRegisters.add("bc");
        allowedRegisters.add("d");
        allowedRegisters.add("e");
        allowedRegisters.add("de");
        allowedRegisters.add("h");
        allowedRegisters.add("l");
        allowedRegisters.add("hl");
        allowedRegisters.add("ixh");
        allowedRegisters.add("ixl");
        allowedRegisters.add("ix");
        allowedRegisters.add("iyh");
        allowedRegisters.add("iyl");
        allowedRegisters.add("iy");
    }
    
    
    public void clearOpGroups()
    {
        allowedOps.clear();
    }


    public void clearAllowedRegisters()
    {
        allowedRegisters.clear();
    }
    
    
    public void addParameter(SourceConstant symbol, int minRange, int maxRange, int signed)
    {
        for(InputParameter p:parameters) {
            if (p.symbol.name.equals(symbol.name)) {
                p.minValue = Math.min(p.minValue, minRange);
                p.maxValue = Math.max(p.maxValue, maxRange);
                return;
            }
        }
        parameters.add(new InputParameter(symbol, minRange, maxRange, signed));
    }
    
    
    public InputParameter getParameter(String name) 
    {
        for(InputParameter p:parameters) {
            if (p.symbol.name.equals(name)) return p;
        }
        return null;
    }
    
    
    public boolean[] getInitialDependencies(List<CPUOpDependency> allDependencies)
    {
        if (initialDependencies != null) return initialDependencies;
        initialDependencies = new boolean[allDependencies.size()];
        for(int i = 0;i<initialDependencies.length;i++) initialDependencies[i] = false;
        
        for(SpecificationExpression exp:startState) {
            CPUOpDependency dep = null;
            if (exp.leftRegister != null) {
                dep = new CPUOpDependency(CPUConstants.registerName(exp.leftRegister).toUpperCase(), null, null, null, null);
            } else if (exp.leftFlagIndex != null) {
                dep = new CPUOpDependency(null, CPUConstants.flagNames[exp.leftFlagIndex], null, null, null);
            } else if (exp.leftConstantMemoryAddress != null) {
                dep = new CPUOpDependency(null, null, null, ""+exp.leftConstantMemoryAddress, ""+exp.leftConstantMemoryAddress);
            } else {
                return null;
            }
            for(int i = 0;i<initialDependencies.length;i++) {
                if (dep.match(allDependencies.get(i))) initialDependencies[i] = true;
            }
        }
        
        return initialDependencies;
    }


    /*
    Note: this is only used by the code generation routines. The code optimization routines use 
    a custom version of this, directly hardcoded in SearchBasedOptimizer.java
    */
    public boolean[] getGoalDependencies(List<CPUOpDependency> allDependencies)
    {
        if (goalDependencies != null) return goalDependencies;
        goalDependencies = new boolean[allDependencies.size()];
        for(int i = 0;i<goalDependencies.length;i++) goalDependencies[i] = false;
        
        for(SpecificationExpression exp:goalState) {
            CPUOpDependency dep = null;
            if (exp.leftRegister != null) {
                dep = new CPUOpDependency(CPUConstants.registerName(exp.leftRegister).toUpperCase(), null, null, null, null);
            } else if (exp.leftFlagIndex != null) {
                dep = new CPUOpDependency(null, CPUConstants.flagNames[exp.leftFlagIndex], null, null, null);
            } else if (exp.leftConstantMemoryAddress != null) {
                dep = new CPUOpDependency(null, null, null, ""+exp.leftConstantMemoryAddress, ""+exp.leftConstantMemoryAddress);
            } else {
                return null;
            }
            for(int i = 0;i<goalDependencies.length;i++) {
                if (dep.match(allDependencies.get(i))) goalDependencies[i] = true;
            }
        }
        
        precomputeGoalDependencyIndexes();
        return goalDependencies;
    }
    
        
    void precomputeGoalDependencyIndexes()
    {
        int nDependencies = 0;
        for(boolean tmp:goalDependencies) {
            if (tmp) nDependencies ++;
        }
        
        goalDependencyIndexes = new int[nDependencies];
        for(int i = 0, j = 0;i<goalDependencies.length;i++) {
            if (goalDependencies[i]) {
                goalDependencyIndexes[j] = i;
                j++;
            }
        }
    }
    
    
    public boolean[] getGoalDependenciesSatisfiedFromTheStart(List<CPUOpDependency> allDependencies)
    {
        if (goalDependenciesSatisfiedFromTheStart != null) return goalDependenciesSatisfiedFromTheStart;
        getGoalDependencies(allDependencies);
        goalDependenciesSatisfiedFromTheStart = new boolean[allDependencies.size()];
        for(int i = 0;i<allDependencies.size();i++) {
            goalDependenciesSatisfiedFromTheStart[i] = false;
            if (goalDependencies[i]) {
                CPUOpDependency dep = allDependencies.get(i);
                if (dep.register != null) {
                    CPUConstants.RegisterNames reg = CPUConstants.registerByName(dep.register);
                    goalDependenciesSatisfiedFromTheStart[i] = true;
                    for(PrecomputedTestCase ptc:precomputedTestCases) {
                        Integer val1 = ptc.getGoalRegisterValue(reg);
                        if (val1 != null) {
                            Integer val2 = ptc.getStartRegisterValue(reg);
                            if (val2 != null && val1.equals(val2)) {
                                // satisfied from the start!
                            } else {
                                goalDependenciesSatisfiedFromTheStart[i] = false;
                                break;
                            }
                        } else {
                            goalDependenciesSatisfiedFromTheStart[i] = false;
                            break;
                        }
                    }
                } else if (dep.flag != null) {
                    goalDependenciesSatisfiedFromTheStart[i] = true;
                    int flag = CPUConstants.flagByName(dep.flag);
                    for(PrecomputedTestCase ptc:precomputedTestCases) {
                        Boolean val1 = ptc.getGoalFlagValue(flag);
                        if (val1 != null) {                        
                            Boolean val2 = ptc.getStartFlagValue(flag);
                            if (val2 != null && val1.equals(val2)) {
                                // satisfied from the start!
                            } else {
                                goalDependenciesSatisfiedFromTheStart[i] = false;
                                break;
                            }
                        } else {
                            goalDependenciesSatisfiedFromTheStart[i] = false;
                            break;
                        }
                    }
                } else if (dep.memoryStart != null) {
                    // For now, assume it's always not satisfied:
                    goalDependenciesSatisfiedFromTheStart[i] = false;
                }
            } else {
                goalDependenciesSatisfiedFromTheStart[i] = true;
            }
        }
        return goalDependenciesSatisfiedFromTheStart;
    }
    
    
    public boolean precomputeTestCases(CodeBase code, MDLConfig config)
    {
        Random rand = new Random();
        precomputedTestCases = new PrecomputedTestCase[numberOfRandomSolutionChecks];
        
        for(int i = 0;i<numberOfRandomSolutionChecks;i++) {
            PrecomputedTestCase testCase = new PrecomputedTestCase();
            
            if (allowRamUse) testCase.trackMemoryWrites = true;
            
            // randomize constants:
            for(InputParameter parameter:parameters) {
                try {
                    int value = parameter.randomValue(rand);
                    parameter.symbol.exp = Expression.constantExpression(value, config);
                    parameter.symbol.clearCache();            
                } catch (Exception e) {
                    config.error(e.getMessage());
                    return false;
                }
            }
            
            int n_startRegisters = 0;
//            int n_startFlags = 0;
            int n_startMemoryAddresses = 0;
            for(SpecificationExpression exp : startState) {
                if (exp.leftRegister != null) n_startRegisters++;
                if (exp.leftFlagIndex != null) {
//                    n_startFlags ++;
                    config.error("Flag values not yet supported in the start state.");
                    return false;
                }
                if (exp.leftConstantMemoryAddress != null) n_startMemoryAddresses ++;
            }
            testCase.startRegisters = new CPUConstants.RegisterNames[n_startRegisters];
            testCase.startRegisterValues = new int[n_startRegisters];
            testCase.startMemoryAddresses = new int[n_startMemoryAddresses];
            testCase.startMemoryValues = new int[n_startMemoryAddresses];
            for(int j = 0, kreg = 0, kmem = 0;j<startState.size();j++) {
                SpecificationExpression exp = startState.get(j);
                Integer value = exp.right.evaluateToInteger(null, code, true);
                if (value == null) {
                    config.error("Cannot evaluate expression " + exp);
                    return false;
                }
                if (exp.leftRegister != null) {
                    testCase.startRegisters[kreg] = exp.leftRegister;
                    if (CPUConstants.is8bitRegister(exp.leftRegister)) {
                        testCase.startRegisterValues[kreg] = InputParameter.eightBitValue(value, exp.right_signed);
//                        System.out.println("input register = " + eightBitValue(value, exp.right_signed));
                    } else {
                        testCase.startRegisterValues[kreg] = InputParameter.sixteenBitValue(value, exp.right_signed);
                    }                
                    kreg++;
                } else if (exp.leftConstantMemoryAddress != null) {
                    testCase.startMemoryAddresses[kmem] = exp.leftConstantMemoryAddress;
                    testCase.startMemoryValues[kmem] = InputParameter.eightBitValue(value, exp.right_signed);;
                    kmem++;
                } else {
                    config.error("Unsupported specification expression: " + exp);
                    return false;
                } 
            }
            int n_goalRegisters = 0;
            int n_goalFlags = 0;
            int n_goalMemoryAddresses = 0;
            for(SpecificationExpression exp : goalState) {
                if (exp.leftRegister != null) n_goalRegisters ++;
                if (exp.leftFlagIndex != null) n_goalFlags ++;
                if (exp.leftConstantMemoryAddress != null) n_goalMemoryAddresses ++;
            }
            testCase.goalRegisters = new CPUConstants.RegisterNames[n_goalRegisters];
            testCase.goalRegisterValues = new int[n_goalRegisters];
            testCase.goalFlags = new int[n_goalFlags];
            testCase.goalFlagValues = new boolean[n_goalFlags];
            testCase.goalMemoryAddresses = new int[n_goalMemoryAddresses];
            testCase.goalMemoryValues = new int[n_goalMemoryAddresses];
            for(int j = 0, kreg = 0, kflag = 0, kmem = 0;j<goalState.size();j++) {
                SpecificationExpression exp = goalState.get(j);
                Integer value = exp.right.evaluateToInteger(null, code, true);
                if (value == null) {
                    config.error("Cannot evaluate expression " + exp);
                    return false;
                }
                if (exp.leftRegister != null) {
                    // It's a register condition:
                    testCase.goalRegisters[j] = exp.leftRegister;
                    if (CPUConstants.is8bitRegister(exp.leftRegister)) {
                        // Since the output check will be done in an unsigned way, we specify the value as unsigned here:
                        testCase.goalRegisterValues[kreg] = InputParameter.eightBitValue(value, InputParameter.UNSIGNED);
                    } else {
                        // Since the output check will be done in an unsigned way, we specify the value as unsigned here:
                        testCase.goalRegisterValues[kreg] = InputParameter.sixteenBitValue(value, InputParameter.UNSIGNED);
//                        System.out.println("goal register = " + sixteenBitValue(value, false));
                    }                
                    kreg++;
                } else if (exp.leftFlagIndex != null) {
                    // it's a flag condition:
                    testCase.goalFlags[j] = CPUConstants.flags[exp.leftFlagIndex];
                    testCase.goalFlagValues[kflag] = value != Expression.FALSE;
                    kflag++;
                } else if (exp.leftConstantMemoryAddress != null) {
                    testCase.goalMemoryAddresses[kmem] = exp.leftConstantMemoryAddress;
                    // Since the output check will be done in an unsigned way, we specify the value as unsigned here:
                    testCase.goalMemoryValues[kmem] = InputParameter.eightBitValue(value, InputParameter.UNSIGNED);;
                    kmem++;
                } else {
                    config.error("Unsupported specification expression: " + exp);
                    return false;
                }
            }
            precomputedTestCases[i] = testCase;
        }
        return true;
    }
    
    
    public List<CPUOpDependency> precomputeAllDependencies()
    {
        // Precompute all the op dependencies before search:
        List<CPUOpDependency> allDependencies = new ArrayList<>();
        for(String regName: new String[]{"A", "B", "C", "D", "E", "H", "L", 
                                         "IXH", "IXL", "IYH", "IYL"}) {
            if (allowedRegisters.contains(regName.toLowerCase())) {
                allDependencies.add(new CPUOpDependency(regName, null, null, null, null));
            }
        }
        for(String flagName: new String[]{"S" ,"Z" ,"H" , "P/V" ,"N" , "C"}) {
            // "H" and "N" are only useful for DAA:
            if (!allowedOps.contains("daa") && flagName.equals("H")) continue;
            if (!allowedOps.contains("daa") && flagName.equals("N")) continue;
            if (flagName.equals("P/V")) {
                if (!allowedOps.contains("jp") &&
                    !allowedOps.contains("ret") &&
                    !allowedOps.contains("call")) {
                    continue;
                }
            }
            allDependencies.add(new CPUOpDependency(null, flagName, null, null, null));
        }
        if (allowIO) {
            allDependencies.add(new CPUOpDependency(null, null, "C", null, null));
        }
        if (allowRamUse) {
            allDependencies.add(new CPUOpDependency(null, null, null, "0", "0x10000"));
        }
        return allDependencies;
    }    
    
    
    @Override
    public String toString() {
        String tmp = "start_state:\n";
        for(SpecificationExpression exp:startState) {
            tmp += exp + "\n";
        }
        tmp += "goal_state:\n";
        for(SpecificationExpression exp:goalState) {
            tmp += exp + "\n";
        }
        return tmp;
    }            
}

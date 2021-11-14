/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import code.CPUOpSpec;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.List;
import util.Resources;
import util.microprocessor.Z80.CPUConstants;

/**
 *
 * @author santi
 */
public class SpecificationParser {
    public static Specification parse(String inputFile, CodeBase code, MDLConfig config)
    {
        try (BufferedReader br = Resources.asReader(inputFile)) {
            Specification spec = new Specification();
            int state = 0;  // 0: start, 1: "allowed_ops", 2: "initial_state", 3: "goal_state", 
                            // 4: 8bit_constants, 5: 16bit_constants, 6: offset_constants, 7: allowed_registers
            
            String line = readNextLine(br);
            while(line != null) {
                List<String> tokens = config.tokenizer.tokenize(line);

                if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("allowed_ops") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    spec.clearOpGroups();
                    state = 1;
                    if (!parseOpGroups(tokens, spec, config)) return  null;
                } else if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("allowed_registers") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    spec.clearAllowedRegisters();
                    state = 7;
                    if (!parseAllowedRegisters(tokens, spec, config)) return null;
                } else if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("initial_state") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        state = 2;
                    } else {
                        config.error("Unexpected token " + tokens.get(0) + " after 'initial_state:'");
                        return null;
                    }
                } else if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("goal_state") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        state = 3;
                    } else {
                        config.error("Unexpected token " + tokens.get(0) + " after 'goal_state:'");
                        return null;
                    }
                } else if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("8bit_constants") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    state = 4;
                    spec.allowed8bitConstants.clear();
                    if (!parse8BitConstants(tokens, line, spec, code, config)) return null;
                } else if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("16bit_constants") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    state = 5;
                    spec.allowed16bitConstants.clear();
                    if (!parse16BitConstants(tokens, line, spec, code, config)) return null;
                } else if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase("offset_constants") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        state = 6;
                        spec.allowedOffsetConstants.clear();
                    } else {
                        config.error("Unexpected token " + tokens.get(0) + " after 'offset_constants:'");
                        return null;
                    }
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("max_ops") && tokens.get(1).equals("=")) {
                    if (config.tokenizer.isInteger(tokens.get(2))) {
                        spec.maxOps = Integer.parseInt(tokens.get(2));
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                        if (!tokens.isEmpty() && config.tokenizer.isSingleLineComment(tokens.get(0))) {
                            config.error("Unexpected token " + tokens.get(0) + " in line " + line);
                            return null;
                        }
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("max_size") && tokens.get(1).equals("=")) {
                    if (config.tokenizer.isInteger(tokens.get(2))) {
                        spec.maxSimulationTime = Integer.parseInt(tokens.get(2));
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                        if (!tokens.isEmpty() && config.tokenizer.isSingleLineComment(tokens.get(0))) {
                            config.error("Unexpected token " + tokens.get(0) + " in line " + line);
                            return null;
                        }
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("max_time") && tokens.get(1).equals("=")) {
                    if (config.tokenizer.isInteger(tokens.get(2))) {
                        spec.maxSimulationTime = Integer.parseInt(tokens.get(2));
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                        if (!tokens.isEmpty() && config.tokenizer.isSingleLineComment(tokens.get(0))) {
                            config.error("Unexpected token " + tokens.get(0) + " in line " + line);
                            return null;
                        }
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("allow_ram_use") && tokens.get(1).equals("=")) {
                    if (tokens.get(2).equalsIgnoreCase("true") || tokens.get(2).equalsIgnoreCase("false")) {
                        spec.allowRamUse = Boolean.parseBoolean(tokens.get(2));
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                        if (!tokens.isEmpty() && config.tokenizer.isSingleLineComment(tokens.get(0))) {
                            config.error("Unexpected token " + tokens.get(0) + " in line " + line);
                            return null;
                        }
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("allow_loops") && tokens.get(1).equals("=")) {
                    if (tokens.get(2).equalsIgnoreCase("true") || tokens.get(2).equalsIgnoreCase("false")) {
                        spec.allowLoops = Boolean.parseBoolean(tokens.get(2));
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                        if (!tokens.isEmpty() && config.tokenizer.isSingleLineComment(tokens.get(0))) {
                            config.error("Unexpected token " + tokens.get(0) + " in line " + line);
                            return null;
                        }
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("n_solution_checks") && tokens.get(1).equals("=")) {
                    if (config.tokenizer.isInteger(tokens.get(2))) {
                        spec.numberOfRandomSolutionChecks = Integer.parseInt(tokens.get(2));
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                        if (!tokens.isEmpty() && config.tokenizer.isSingleLineComment(tokens.get(0))) {
                            config.error("Unexpected token " + tokens.get(0) + " in line " + line);
                            return null;
                        }
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                    
                } else if (tokens.size()>=3 && tokens.get(0).equalsIgnoreCase("goal") && tokens.get(1).equals("=")) {
                    if (tokens.get(2).equalsIgnoreCase("ops")) {
                        spec.searchType = SearchBasedOptimizer.SEARCH_ID_OPS;
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                    } else if (tokens.get(2).equalsIgnoreCase("size")) {
                        spec.searchType = SearchBasedOptimizer.SEARCH_ID_BYTES;
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                    } else if (tokens.get(2).equalsIgnoreCase("time")) {
                        spec.searchType = SearchBasedOptimizer.SEARCH_ID_CYCLES;
                        tokens.remove(0);
                        tokens.remove(0);
                        tokens.remove(0);
                    } else {
                        config.error("Cannot parse line " + line);
                        return null;
                    }
                } else {
                    switch(state) {
                        case 0:
                            // start:
                            if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                                config.error("Expecting 'allowed_ops:', 'initial_state:', or 'goal_state:', but found " + tokens.get(0));
                                return null;
                            }
                            break;

                        case 1:
                            if (!parseOpGroups(tokens, spec, config)) return null;
                            break;

                        case 7:
                            if (!parseAllowedRegisters(tokens, spec, config)) return null;
                            break;

                        case 2:
                            // parse initial state statements:
                            if (!parseSpecificationExpression(tokens, true, line, spec, code, config)) return null;
                            break;
                        case 3:
                            // parsing the goal state:
                            if (!parseSpecificationExpression(tokens, false, line, spec, code, config)) return null;
                            break;
                        case 4:
                            if (!parse8BitConstants(tokens, line, spec, code, config)) return null;
                            break;
                        case 5:
                            if (!parse16BitConstants(tokens, line, spec, code, config)) return null;
                            break;
                        case 6:
                        {
                            // parsing offset bit constants:
                            Expression exp = config.expressionParser.parse(tokens, null, null, code);
                            if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                                spec.allowedOffsetConstants.add(exp.integerConstant);
                            } else if (exp.type == Expression.EXPRESSION_SUB &&
                                       exp.args.size() == 2 &&
                                       exp.args.get(0).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                                       exp.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT) {
                                for(int i = exp.args.get(0).integerConstant; i<=exp.args.get(1).integerConstant; i++) {
                                    spec.allowedOffsetConstants.add(i);
                                }
                            } else {
                                config.error("Cannot parse constant definition in line " + line);
                                return null;
                            }
                        }   break;
                    }
                }
                line = readNextLine(br);
            }
            
            // Find constants, and make sure all constants in the goal are defined:
            for(SpecificationExpression sexp: spec.startState) {
                Expression exp = sexp.right;
                for(String symbolName: exp.getAllSymbols()) {
                    SourceConstant symbol = new SourceConstant(symbolName, symbolName, exp, null, config);
                    code.addSymbol(symbolName, symbol);
                    if (CPUConstants.is8bitRegister(sexp.leftRegister)) {
                        spec.addParameter(symbol, 0, 0xff);
                    } else {
                        spec.addParameter(symbol, 0, 0xffff);
                    }
                }
            }
            for(SpecificationExpression sexp: spec.goalState) {
                Expression exp = sexp.right;
                for(String symbol: exp.getAllSymbols()) {
                    if (spec.getParameter(symbol) == null) {
                        config.error("Constant " + symbol + " used in goal state is not defined.");
                        return null;
                    }
                }
            }
            
            return spec;
        } catch (Exception e) {
            config.error("Exception while trying to parse specification file '"+inputFile+"'");
            config.error("Exception message: " + Arrays.toString(e.getStackTrace()));
            return null;
        }
    }
    
    
    static String readNextLine(BufferedReader br) throws Exception
    {
        String line = br.readLine();
        if (line != null) line = line.trim();
        // The backslash character can be used to break a line:
        while(line!=null && line.endsWith("\\")) {
            String line2 = br.readLine().trim();
            if (line2 != null) {
                line = line.substring(0, line.length()-1).trim() + " " + line2;
            } else {
                break;
            }
        }
        return line;
    }
    
    
    static boolean parse8BitConstants(List<String> tokens, String line, Specification spec, CodeBase code, MDLConfig config)
    {
        if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
            return true;
        }
        
        // parsing 8 bit constants:
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            spec.allowed8bitConstants.add(exp.integerConstant);
        } else if (exp.type == Expression.EXPRESSION_SUB &&
                   exp.args.size() == 2 &&
                   exp.args.get(0).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                   exp.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            for(int i = exp.args.get(0).integerConstant; i<=exp.args.get(1).integerConstant; i++) {
                spec.allowed8bitConstants.add(i);
            }
        } else {
            config.error("Cannot parse constant definition in line " + line);
            return false;
        }
        
        if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
            return true;
        }

        if (!tokens.get(0).equals(",")) {
            return false;
        }
        tokens.remove(0);
        
        return parse8BitConstants(tokens, line, spec, code, config);
    }
    
    
    
    static boolean parse16BitConstants(List<String> tokens, String line, Specification spec, CodeBase code, MDLConfig config)
    {
        if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
            return true;
        }
        
        // parsing 8 bit constants:
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        if (exp.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            spec.allowed16bitConstants.add(exp.integerConstant);
        } else if (exp.type == Expression.EXPRESSION_SUB &&
                   exp.args.size() == 2 &&
                   exp.args.get(0).type == Expression.EXPRESSION_INTEGER_CONSTANT &&
                   exp.args.get(1).type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            for(int i = exp.args.get(0).integerConstant; i<=exp.args.get(1).integerConstant; i++) {
                spec.allowed16bitConstants.add(i);
            }
        } else {
            config.error("Cannot parse constant definition in line " + line);
            return false;
        }
        
        if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
            return true;
        }

        if (!tokens.get(0).equals(",")) {
            return false;
        }
        tokens.remove(0);
        
        return parse16BitConstants(tokens, line, spec, code, config);
    }
    
    
    static boolean parseOpGroups(List<String> tokens, Specification spec, MDLConfig config)
    {
        // parse initial state statements:
        while(!tokens.isEmpty()) {
            String opGroup = tokens.remove(0);
            switch(opGroup.toLowerCase()) {
                case "logic":
                    spec.allowedOps.add("and");
                    spec.allowedOps.add("or");
                    spec.allowedOps.add("xor");
                    break;
                case "increment":
                    spec.allowedOps.add("inc");
                    spec.allowedOps.add("dec");
                    break;
                case "addition":
                    spec.allowedOps.add("add");
                    spec.allowedOps.add("adc");
                    spec.allowedOps.add("sub");
                    spec.allowedOps.add("sbc");
                    break;
                case "ld":
                    spec.allowedOps.add("ld");
                    break;
                case "rotation":
                    spec.allowedOps.add("rlc");
                    spec.allowedOps.add("rl");
                    spec.allowedOps.add("rrc");
                    spec.allowedOps.add("rr");
                    spec.allowedOps.add("rlca");
                    spec.allowedOps.add("rla");
                    spec.allowedOps.add("rrca");
                    spec.allowedOps.add("rra");
                    break;
                case "shift":
                    spec.allowedOps.add("sla");
                    spec.allowedOps.add("sra");
                    spec.allowedOps.add("srl");
                    spec.allowedOps.add("sli");
                    break;
                case "negation":
                    spec.allowedOps.add("cpl");
                    spec.allowedOps.add("neg");
                    break;
                case "bits":
                    spec.allowedOps.add("bit");
                    spec.allowedOps.add("set");
                    spec.allowedOps.add("res");
                    break;
                case "carry":
                    spec.allowedOps.add("ccf");
                    spec.allowedOps.add("scf");
                    break;
                case "jump":
                    spec.allowedOps.add("jp");
                    spec.allowedOps.add("jr");
                    spec.allowedOps.add("djnz");
                    break;
                default:
                {
                    List<CPUOpSpec> l = config.opParser.getOpSpecs(opGroup.toLowerCase());
                    if (l == null || l.isEmpty()) {
                        config.error("Unrecognized CPU op, or op group: " + opGroup);
                        return false;
                    } else {
                        spec.allowedOps.add(opGroup.toLowerCase());
                    }
                }
            }
            if (!tokens.isEmpty()) {
                if (tokens.get(0).equals(",")) {
                    tokens.remove(0);
                } else if (config.tokenizer.isSingleLineComment(tokens.get(0))) {
                    return true;
                } else {
                    config.error("Unexpected token " + tokens.get(0));
                    return false;
                }
            }
        }
        return true;
    }
    
    
    
    static boolean parseAllowedRegisters(List<String> tokens, Specification spec, MDLConfig config)
    {
        while(!tokens.isEmpty()) {
            String register = tokens.remove(0);
            switch(register.toLowerCase()) {
                case "a":
                    spec.allowedRegisters.add("a");
                    break;
                case "af":
                    spec.allowedRegisters.add("af");
                    break;
                case "b":
                    spec.allowedRegisters.add("b");
                    break;
                case "c":
                    spec.allowedRegisters.add("c");
                    break;
                case "bc":
                    spec.allowedRegisters.add("b");
                    spec.allowedRegisters.add("c");
                    spec.allowedRegisters.add("bc");
                    break;
                case "d":
                    spec.allowedRegisters.add("d");
                    break;
                case "e":
                    spec.allowedRegisters.add("e");
                    break;
                case "de":
                    spec.allowedRegisters.add("d");
                    spec.allowedRegisters.add("e");
                    spec.allowedRegisters.add("de");
                    break;
                case "h":
                    spec.allowedRegisters.add("h");
                    break;
                case "l":
                    spec.allowedRegisters.add("l");
                    break;
                case "hl":
                    spec.allowedRegisters.add("h");
                    spec.allowedRegisters.add("l");
                    spec.allowedRegisters.add("hl");
                    break;
                case "ixh":
                    spec.allowedRegisters.add("ixh");
                    break;
                case "ixl":
                    spec.allowedRegisters.add("ixl");
                    break;
                case "ix":
                    spec.allowedRegisters.add("ixh");
                    spec.allowedRegisters.add("ixl");
                    spec.allowedRegisters.add("ix");
                    break;
                case "iyh":
                    spec.allowedRegisters.add("iyh");
                    break;
                case "iyl":
                    spec.allowedRegisters.add("iyl");
                    break;
                case "iy":
                    spec.allowedRegisters.add("iyh");
                    spec.allowedRegisters.add("iyl");
                    spec.allowedRegisters.add("iy");
                    break;
                case "sp":
                    spec.allowedRegisters.add("sp");
                    break;
                case "r":
                    spec.allowedRegisters.add("r");
                    break;
                case "i":
                    spec.allowedRegisters.add("i");
                    break;
                default:
                    config.error("Unrecognized register:" + register);
                    return false;
            }
            if (!tokens.isEmpty()) {
                if (tokens.get(0).equals(",")) {
                    tokens.remove(0);
                } else if (config.tokenizer.isSingleLineComment(tokens.get(0))) {
                    return true;
                } else {
                    config.error("Unexpected token " + tokens.get(0));
                    return false;
                }
            }
        }
        
        if (spec.allowedRegisters.contains("b") && spec.allowedRegisters.contains("c") &&
            !spec.allowedRegisters.contains("bc")) {
            spec.allowedRegisters.add("bc");
        }
        if (spec.allowedRegisters.contains("d") && spec.allowedRegisters.contains("e") &&
            !spec.allowedRegisters.contains("de")) {
            spec.allowedRegisters.add("de");
        }
        if (spec.allowedRegisters.contains("h") && spec.allowedRegisters.contains("l") &&
            !spec.allowedRegisters.contains("hl")) {
            spec.allowedRegisters.add("hl");
        }
        if (spec.allowedRegisters.contains("ixh") && spec.allowedRegisters.contains("ixl") &&
            !spec.allowedRegisters.contains("ix")) {
            spec.allowedRegisters.add("ix");
        }
        if (spec.allowedRegisters.contains("iyh") && spec.allowedRegisters.contains("iyl") &&
            !spec.allowedRegisters.contains("iy")) {
            spec.allowedRegisters.add("iy");
        }
        
        return true;
    }
    
    
    public static boolean parseSpecificationExpression(
            List<String> tokens, boolean isStartState, String line, 
            Specification spec, CodeBase code, MDLConfig config)
    {
        if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
            return true;
        }
        
        SpecificationExpression specExp = new SpecificationExpression();
        
        String token = tokens.remove(0);
        if (token.equals("(")) {
            // memory address:
            Expression exp = config.expressionParser.parse(tokens, null, null, code);
            if (exp == null) {
                config.error("Cannot parse left-hand side expression in: " + line);
                return false;
            }
            token = tokens.remove(0);
            if (!token.equals(")")) {
                config.error("Cannot parse left-hand side expression in: " + line);
                return false;
            }
            Integer val = exp.evaluateToInteger(null, code, true);
            if (val == null) {
                config.error("Cannot evaluate address in left-hand side expression to a constant in: " + line);
                return false;
            }
            specExp.leftConstantMemoryAddress = val;
        } else {
            specExp.leftRegister = CPUConstants.registerByName(token.toLowerCase());
            if (specExp.leftRegister == null) {
                switch(token.toLowerCase()) {
                    // flag_C, flag_N, flag_PV, flag_3, flag_H, flag_5, flag_Z, flag_S
                    case "c_flag":
                        specExp.leftFlagIndex = 0;
                        break;
                    case "n_flag":
                        specExp.leftFlagIndex = 1;
                        break;
                    case "pv_flag":
                        specExp.leftFlagIndex = 2;
                        break;
                    case "h_flag":
                        specExp.leftFlagIndex = 4;
                        break;
                    case "z_flag":
                        specExp.leftFlagIndex = 6;
                        break;
                    case "s_flag":
                        specExp.leftFlagIndex = 7;
                        break;

                    default:
                        config.error("Unknown register/flag " + token);
                        return false;
                }
            }
        }
        
        if (tokens.isEmpty() || (!tokens.get(0).equals("=") && !tokens.get(0).equals("=="))) {
            config.error("Expected '=' after left-hand-side expression in line: " + line);
            return false;
        }
        tokens.remove(0); // =
        
        specExp.right = config.expressionParser.parse(tokens, null, null, code);
        if (specExp.right == null) {
            config.error("Cannot parse left-hand-side of expression in line: " + line);
            return false;
        }
        
        if (isStartState) {
            spec.startState.add(specExp);
        } else {
            spec.goalState.add(specExp);
        }
        
        return true;
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import java.io.BufferedReader;
import java.util.List;
import util.Resources;
import util.microprocessor.Z80.CPUConstants;

/**
 *
 * @author santi
 */
public class SpecificationParser {
    public static Specification parse(String inputFile, MDLConfig config)
    {
        try (BufferedReader br = Resources.asReader(inputFile)) {
            Specification spec = new Specification();
            int state = 0;  // 0: start, 1: "allowed_ops", 2: "initial_state", 3: "goal_state"
            
            String line = br.readLine().trim();
            while(line != null) {
                List<String> tokens = config.tokenizer.tokenize(line);

                if (tokens.size()>=2 && tokens.get(0).equals("allowed_ops") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        state = 1;
                        spec.clearOpGroups();
                    } else {
                        config.error("Unexpected token " + tokens.get(0) + " after 'allowed_ops:'");
                        return null;
                    }
                } else if (tokens.size()>=2 && tokens.get(0).equals("initial_state") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        state = 2;
                    } else {
                        config.error("Unexpected token " + tokens.get(0) + " after 'initial_state:'");
                        return null;
                    }
                } if (tokens.size()>=2 && tokens.get(0).equals("goal_state") && tokens.get(1).equals(":")) {
                    tokens.remove(0);
                    tokens.remove(0);
                    if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
                        state = 3;
                    } else {
                        config.error("Unexpected token " + tokens.get(0) + " after 'goal_state:'");
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
                            // parse initial state statements:
                            if (!tokens.isEmpty()) {
                                String opGroup = tokens.remove(0);
                                switch(opGroup) {
                                    case "andorxor":
                                        spec.allowAndOrXorOps = true;
                                        break;
                                    case "incdec":
                                        spec.allowIncDecOps = true;
                                        break;
                                    default:
                                        config.error("Unrecognized op group " + opGroup);
                                        return null;                                        
                                }
                                if (!tokens.isEmpty() && !config.tokenizer.isSingleLineComment(tokens.get(0))) {
                                    config.error("Unexpected token " + tokens.get(0));
                                    return null;
                                }
                            }
                            break;

                        case 2:
                            // parse initial state statements:
                            if (!parseSpecificationExpression(tokens, true, line, spec, config)) return null;
                            break;
                        case 3:
                            // parsing the goal state:
                            if (!parseSpecificationExpression(tokens, false, line, spec, config)) return null;
                            break;
                    }
                }
                line = br.readLine();
            }
            
            return spec;
        } catch (Exception e) {
            config.error("Exception while trying to parse specificaiton file '"+inputFile+"'");
            config.error("Exception message: " + e.getMessage());
            return null;
        }
    }
    
    
    public static boolean parseSpecificationExpression(List<String> tokens, boolean isStartState, String line, Specification spec, MDLConfig config)
    {
        if (tokens.isEmpty() || config.tokenizer.isSingleLineComment(tokens.get(0))) {
            return true;
        }
        
        Specification.SpecificationExpression specExp = new Specification.SpecificationExpression();
        
        specExp.leftRegisterName = tokens.remove(0);
        switch(specExp.leftRegisterName) {
            case "a":
            case "A":
                specExp.leftRegister = CPUConstants.RegisterNames.A;
                break;
            case "a'":
            case "A'":
                specExp.leftRegister = CPUConstants.RegisterNames.A_ALT;
                break;
            case "f":
            case "F":
                specExp.leftRegister = CPUConstants.RegisterNames.F;
                break;
            case "f'":
            case "F'":
                specExp.leftRegister = CPUConstants.RegisterNames.F_ALT;
                break;
            case "bc":
            case "BC":
                specExp.leftRegister = CPUConstants.RegisterNames.BC;
                break;
            case "b":
            case "B":
                specExp.leftRegister = CPUConstants.RegisterNames.B;
                break;
            case "c":
            case "C":
                specExp.leftRegister = CPUConstants.RegisterNames.C;
                break;
            case "de":
            case "DE":
                specExp.leftRegister = CPUConstants.RegisterNames.DE;
                break;
            case "d":
            case "D":
                specExp.leftRegister = CPUConstants.RegisterNames.E;
                break;
            case "e":
            case "E":
                specExp.leftRegister = CPUConstants.RegisterNames.E;
                break;
            case "hl":
            case "HL":
                specExp.leftRegister = CPUConstants.RegisterNames.HL;
                break;
            case "h":
            case "H":
                specExp.leftRegister = CPUConstants.RegisterNames.H;
                break;
            case "l":
            case "L":
                specExp.leftRegister = CPUConstants.RegisterNames.L;
                break;
                
            case "bc'":
            case "BC'":
                specExp.leftRegister = CPUConstants.RegisterNames.BC_ALT;
                break;
            case "b'":
            case "B'":
                specExp.leftRegister = CPUConstants.RegisterNames.B_ALT;
                break;
            case "c'":
            case "C'":
                specExp.leftRegister = CPUConstants.RegisterNames.C_ALT;
                break;
            case "de'":
            case "DE'":
                specExp.leftRegister = CPUConstants.RegisterNames.DE_ALT;
                break;
            case "d'":
            case "D'":
                specExp.leftRegister = CPUConstants.RegisterNames.E_ALT;
                break;
            case "e'":
            case "E'":
                specExp.leftRegister = CPUConstants.RegisterNames.E_ALT;
                break;
            case "hl'":
            case "HL'":
                specExp.leftRegister = CPUConstants.RegisterNames.HL_ALT;
                break;
            case "h'":
            case "H'":
                specExp.leftRegister = CPUConstants.RegisterNames.H_ALT;
                break;
            case "l'":
            case "L'":
                specExp.leftRegister = CPUConstants.RegisterNames.L_ALT;
                break;                

            case "ix":
            case "IX":
                specExp.leftRegister = CPUConstants.RegisterNames.IX;
                break;
            case "ixh":
            case "IXH":
                specExp.leftRegister = CPUConstants.RegisterNames.IXH;
                break;
            case "ixl":
            case "IXL":
                specExp.leftRegister = CPUConstants.RegisterNames.IXL;
                break;                

            case "iy":
            case "IY":
                specExp.leftRegister = CPUConstants.RegisterNames.IY;
                break;
            case "iyh":
            case "IYH":
                specExp.leftRegister = CPUConstants.RegisterNames.IYH;
                break;
            case "iyl":
            case "IYL":
                specExp.leftRegister = CPUConstants.RegisterNames.IYL;
                break;                

            case "sp":
            case "SP":
                specExp.leftRegister = CPUConstants.RegisterNames.SP;
                break;
            case "pc":
            case "PC":
                specExp.leftRegister = CPUConstants.RegisterNames.PC;
                break;
        }
        
        if (tokens.isEmpty() || (!tokens.get(0).equals("=") && !tokens.get(0).equals("=="))) {
            config.error("Expected '=' after left-hand-side expression in line: " + line);
            return false;
        }
        tokens.remove(0); // =
        
        specExp.right = config.expressionParser.parse(tokens, null, null, null);
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

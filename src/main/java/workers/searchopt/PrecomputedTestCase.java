/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package workers.searchopt;

import cl.MDLConfig;
import util.microprocessor.TrackingZ80Memory;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class PrecomputedTestCase {
    CPUConstants.RegisterNames startRegisters[] = null;
    int startRegisterValues[] = null;
    int startMemoryAddresses[] = null;
    int startMemoryValues[] = null;
    
    CPUConstants.RegisterNames goalRegisters[] = null;
    int goalRegisterValues[] = null;
    int goalFlags[] = null;
    boolean goalFlagValues[] = null;
    int goalMemoryAddresses[] = null;
    int goalMemoryValues[] = null;
    
    boolean trackMemoryWrites = false;


    public void initCPU(Z80Core z80)
    {
        for(int i = 0;i<startRegisters.length;i++) {
            z80.setRegisterValue(startRegisters[i], startRegisterValues[i]);
        }
        if (startMemoryAddresses != null) {
            for(int i = 0;i<startMemoryAddresses.length;i++) {
                z80.writeByte(startMemoryAddresses[i], startMemoryValues[i]);
            }
        }
        if (trackMemoryWrites) {
            ((TrackingZ80Memory)z80.getRAM()).clearMemoryAccesses();
        }        
    }


    public boolean checkGoalState(Z80Core z80)
    {
        for(int i = 0;i<goalRegisters.length;i++) {
            if (z80.getRegisterValue(goalRegisters[i]) != goalRegisterValues[i]) {
//                System.out.println("register mismatch: " + goalRegisters[i] + " -> " + z80.getRegisterValue(goalRegisters[i]) + " vs " + goalRegisterValues[i]);
//                for(int j = 0;j<startRegisters.length;j++) {
//                    System.out.println("    initial " + startRegisters[j] + " = " + startRegisterValues[j]);
//                }
                return false;
            }
        }
        for(int i = 0;i<goalFlags.length;i++) {
             if (z80.getFlagValue(CPUConstants.flagIndex(goalFlags[i])) != goalFlagValues[i]) return false;
        }
        if (goalMemoryAddresses != null) {
            for(int i = 0;i<goalMemoryAddresses.length;i++) {
                if (z80.readByte(goalMemoryAddresses[i]) != goalMemoryValues[i]) return false;
            }
        }
        
        if (trackMemoryWrites) {
            for(Integer address:((TrackingZ80Memory)z80.getRAM()).getMemoryWrites()) {
                boolean found = false;
                for(int i = 0;i<goalMemoryAddresses.length;i++) {
                    if (address == goalMemoryAddresses[i]) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
        }

//        for(int i = 0;i<goalRegisters.length;i++) {
//            System.out.println(goalRegisters[i] + ": " + goalRegisterValues[i]);
//        }
        
        return true;
    }        


    public boolean checkGoalStateDebug(Z80Core z80, MDLConfig config)
    {
        for(int i = 0;i<goalRegisters.length;i++) {
            if (z80.getRegisterValue(goalRegisters[i]) != goalRegisterValues[i]) {
                config.info("Register mismatch: " + CPUConstants.registerName(goalRegisters[i]) + " != " + goalRegisterValues[i]);
                return false;
            }
        }
        for(int i = 0;i<goalFlags.length;i++) {
             if (z80.getFlagValue(CPUConstants.flagIndex(goalFlags[i])) != goalFlagValues[i]) {
                config.info("Flag mismatch: " + goalFlags[i] + " != " + goalFlagValues[i]);
                return false;
             }
        }
        for(int i = 0;i<goalMemoryAddresses.length;i++) {
            if (z80.readByte(goalMemoryAddresses[i]) != goalMemoryValues[i]) {
                config.info("RAM position " + goalMemoryAddresses[i] + " does not contain byte " + goalMemoryValues[i]);
                return false;
            }
        }
        if (trackMemoryWrites) {
            for(Integer address:((TrackingZ80Memory)z80.getRAM()).getMemoryWrites()) {
                boolean found = false;
                for(int i = 0;i<goalMemoryAddresses.length;i++) {
                    if (address == goalMemoryAddresses[i]) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    config.info("RAM position " + address + " was written to, but it shouldn't have!");
                    return false;
                }
            }
        }
        
        return true;
    }


    public Integer getStartRegisterValue(CPUConstants.RegisterNames reg)
    {
        for(int i = 0;i<startRegisters.length;i++) {
            if (startRegisters[i] == reg) {
                return startRegisterValues[i];
            } else {
                if ((startRegisters[i] == CPUConstants.RegisterNames.HL &&
                     reg == CPUConstants.RegisterNames.H) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.DE &&
                     reg == CPUConstants.RegisterNames.D) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.BC &&
                     reg == CPUConstants.RegisterNames.B) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.HL_ALT &&
                     reg == CPUConstants.RegisterNames.H_ALT) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.DE_ALT &&
                     reg == CPUConstants.RegisterNames.D_ALT) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.BC_ALT &&
                     reg == CPUConstants.RegisterNames.B_ALT) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.IX &&
                     reg == CPUConstants.RegisterNames.IXH) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.IY &&
                     reg == CPUConstants.RegisterNames.IYH)
                    ) {
                    return (startRegisterValues[i] >> 8) & 0xff;
                }
                if ((startRegisters[i] == CPUConstants.RegisterNames.HL &&
                     reg == CPUConstants.RegisterNames.L) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.DE &&
                     reg == CPUConstants.RegisterNames.E) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.BC &&
                     reg == CPUConstants.RegisterNames.C) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.HL_ALT &&
                     reg == CPUConstants.RegisterNames.L_ALT) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.DE_ALT &&
                     reg == CPUConstants.RegisterNames.E_ALT) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.BC_ALT &&
                     reg == CPUConstants.RegisterNames.C_ALT) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.IX &&
                     reg == CPUConstants.RegisterNames.IXL) ||
                    (startRegisters[i] == CPUConstants.RegisterNames.IY &&
                     reg == CPUConstants.RegisterNames.IYL)
                    ) {
                    return startRegisterValues[i] & 0xff;
                }
            }
        }
        return null;
    }


    public Integer getGoalRegisterValue(CPUConstants.RegisterNames reg)
    {
        for(int i = 0;i<goalRegisters.length;i++) {
            if (goalRegisters[i] == reg) {
                return goalRegisterValues[i];
            } else {
                if ((goalRegisters[i] == CPUConstants.RegisterNames.HL &&
                     reg == CPUConstants.RegisterNames.H) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.DE &&
                     reg == CPUConstants.RegisterNames.D) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.BC &&
                     reg == CPUConstants.RegisterNames.B) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.HL_ALT &&
                     reg == CPUConstants.RegisterNames.H_ALT) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.DE_ALT &&
                     reg == CPUConstants.RegisterNames.D_ALT) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.BC_ALT &&
                     reg == CPUConstants.RegisterNames.B_ALT) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.IX &&
                     reg == CPUConstants.RegisterNames.IXH) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.IY &&
                     reg == CPUConstants.RegisterNames.IYH)
                    ) {
                    return (goalRegisterValues[i] >> 8) & 0xff;
                }
                if ((goalRegisters[i] == CPUConstants.RegisterNames.HL &&
                     reg == CPUConstants.RegisterNames.L) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.DE &&
                     reg == CPUConstants.RegisterNames.E) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.BC &&
                     reg == CPUConstants.RegisterNames.C) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.HL_ALT &&
                     reg == CPUConstants.RegisterNames.L_ALT) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.DE_ALT &&
                     reg == CPUConstants.RegisterNames.E_ALT) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.BC_ALT &&
                     reg == CPUConstants.RegisterNames.C_ALT) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.IX &&
                     reg == CPUConstants.RegisterNames.IXL) ||
                    (goalRegisters[i] == CPUConstants.RegisterNames.IY &&
                     reg == CPUConstants.RegisterNames.IYL)
                    ) {
                    return goalRegisterValues[i] & 0xff;
                }
            }
        }
        return null;
    }        


    public Boolean getStartFlagValue(int flag)
    {
        Integer F = getStartRegisterValue(CPUConstants.RegisterNames.F);
        if (F == null) return null;
        return (F & flag) != 0;
    }


    public Boolean getGoalFlagValue(int flag)
    {
        Integer F = getGoalRegisterValue(CPUConstants.RegisterNames.F);
        if (F != null) {
            return (F & flag) != 0;
        }
        for(int i = 0;i<goalFlags.length;i++) {
            if (goalFlags[i] == flag) {
                return goalFlagValues[i];
            }
        }
        return null;
    }    
    
    
    @Override
    public String toString()
    {
        // This is just a debugging function, which gets changed to print
        // whichever informatino is useful whenever I am debugging.
        /*
        CPUConstants.RegisterNames startRegisters[] = null;
        int startRegisterValues[] = null;
        int startMemoryAddresses[] = null;
        int startMemoryValues[] = null;

        CPUConstants.RegisterNames goalRegisters[] = null;
        int goalRegisterValues[] = null;
        int goalFlags[] = null;
        boolean goalFlagValues[] = null;
        int goalMemoryAddresses[] = null;
        int goalMemoryValues[] = null;
        */
        String str = "";
        str += "GOAL: ";
        for(int i = 0;i<goalRegisterValues.length;i++) {
            str += CPUConstants.registerName(goalRegisters[i]) + "=" + goalRegisterValues[i] + ", ";
        }
        return str;
    }
}

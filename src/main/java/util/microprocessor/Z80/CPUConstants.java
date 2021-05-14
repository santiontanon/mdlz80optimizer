/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * - Original code borrowed from https://github.com/codesqueak/Z80Processor
 * - Modifications to integrate into MDL by Santiago Ontañón
 */

package util.microprocessor.Z80;

public class CPUConstants {
    // Pre-calculate parity table
    static final boolean[] PARITY_TABLE = new boolean[256];

    // register bit codes
    final static int regCodeB = 0x0000;
    final static int regCodeC = 0x0001;
    final static int regCodeD = 0x0002;
    final static int regCodeE = 0x0003;
    final static int regCodeH = 0x0004;
    final static int regCodeL = 0x0005;
    final static int regCodeM = 0x0006;
    final static int regCodeF = 0x0006;
    final static int regCodeA = 0x0007;
    final static int regCodeIXH = 0x0004;
    final static int regCodeIXL = 0x0005;
    final static int regCodeBC = 0x0000;
    final static int regCodeDE = 0x0001;
    final static int regCodeHL = 0x0002;
    final static int regCodeSP = 0x0003;
    // or mask values
    final static int setBit0 = 0x0001;
    final static int setBit1 = 0x0002;
    final static int setBit2 = 0x0004;
    final static int setBit3 = 0x0008;
    final static int setBit4 = 0x0010;
    final static int setBit5 = 0x0020;
    final static int setBit6 = 0x0040;
    final static int setBit7 = 0x0080;
    // and mask values
    final static int resetBit0 = setBit0 ^ 0x00FF;
    final static int resetBit1 = setBit1 ^ 0x00FF;
    final static int resetBit2 = setBit2 ^ 0x00FF;
    final static int resetBit3 = setBit3 ^ 0x00FF;
    final static int resetBit4 = setBit4 ^ 0x00FF;
    final static int resetBit5 = setBit5 ^ 0x00FF;
    final static int resetBit6 = setBit6 ^ 0x00FF;
    final static int resetBit7 = setBit7 ^ 0x00FF;
    // flag register bit positions for setting
    final static int flag_S = 0x0080;
    final static int flag_Z = 0x0040;
    final static int flag_5 = 0x0020;
    public final static int flag_H = 0x0010;
    final static int flag_3 = 0x0008;
    public final static int flag_PV = 0x0004;
    public final static int flag_N = 0x0002;
    final static int flag_C = 0x0001;
    // for resetting
    final static int flag_S_N = 0x007F;
    final static int flag_Z_N = 0x00BF;
    public final static int flag_5_N = 0x00DF;
    final static int flag_H_N = 0x00EF;
    public final static int flag_3_N = 0x00F7;
    final static int flag_PV_N = 0x00FB;
    final static int flag_N_N = 0x00FD;
    final static int flag_C_N = 0x00FE;
    /* LSB, MSB masking values */
    final static int lsb = 0x00FF;
    final static int msb = 0xFF00;
    final static int lsw = 0x0000FFFF;
    
    public static int flags[] = {flag_C, flag_N, flag_PV, flag_3, flag_H, flag_5, flag_Z, flag_S};
    
    public static String flagNames[] = {"C", "N", "P/V", "<unused3>", "H", "<unused5>", "Z", "S"};
    
    public static final RegisterNames allRegisters[] = {
        RegisterNames.BC, RegisterNames.DE, RegisterNames.HL,
        RegisterNames.BC_ALT, RegisterNames.DE_ALT, RegisterNames.HL_ALT,
        RegisterNames.IX, RegisterNames.IY,
        RegisterNames.SP, RegisterNames.PC,        
        RegisterNames.A, RegisterNames.F,
        RegisterNames.B, RegisterNames.C,
        RegisterNames.D, RegisterNames.E,
        RegisterNames.H, RegisterNames.L,
        RegisterNames.A_ALT, RegisterNames.F_ALT,
        RegisterNames.B_ALT, RegisterNames.C_ALT,
        RegisterNames.D_ALT, RegisterNames.E_ALT,
        RegisterNames.H_ALT, RegisterNames.L_ALT,
        RegisterNames.IXH, RegisterNames.IXL,
        RegisterNames.IYH, RegisterNames.IYL,
        RegisterNames.R
    };        

    
    public static final RegisterNames eightBitRegisters[] = {
        RegisterNames.A, RegisterNames.F,
        RegisterNames.B, RegisterNames.C,
        RegisterNames.D, RegisterNames.E,
        RegisterNames.H, RegisterNames.L,
        RegisterNames.A_ALT, RegisterNames.F_ALT,
        RegisterNames.B_ALT, RegisterNames.C_ALT,
        RegisterNames.D_ALT, RegisterNames.E_ALT,
        RegisterNames.H_ALT, RegisterNames.L_ALT,
        RegisterNames.IXH, RegisterNames.IXL,
        RegisterNames.IYH, RegisterNames.IYL,
        RegisterNames.R
    };        

    static {
        PARITY_TABLE[0] = true; // even PARITY_TABLE seed value
        int position = 1; // table position
        for (int bit = 0; bit < 8; bit++) {
            for (int fill = 0; fill < position; fill++) {
                PARITY_TABLE[position + fill] = !PARITY_TABLE[fill];
            }
            position = position * 2;
        }
    }

    /**
     * Stop construction
     */
    private CPUConstants() {
    }

    /**
     * All supported processor registers which can be accessed externally to the core
     */
    public enum RegisterNames {
        /**
         * 16 bit BC register pair
         */
        BC,
        /**
         * 16 bit DE register pair
         */
        DE,
        /**
         * 16 bit HL register pair
         */
        HL,
        /**
         * Alternate register file 16 bit BC register pair
         */
        BC_ALT,
        /**
         * Alternate register file 16 bit DE register pair
         */
        DE_ALT,
        /**
         * Alternate register file 16 bit HL register pair
         */
        HL_ALT,
        /**
         * IX 16 bit index register
         */
        IX,
        /**
         * IY 16 bit index register
         */
        IY,
        /**
         * Stack pointer
         */
        SP,
        /**
         * Program counter
         */
        PC,
        /**
         * 8 bit accumulator
         */
        A,
        /**
         * 8 bit flag register
         */
        F,
        /**
         * Alternate 8 bit accumulator
         */
        A_ALT,
        /**
         * Alternate 8 bit flag register
         */
        F_ALT,
        /**
         * 8 bit interrupt register
         */
        I,
        /**
         * 7 bit refresh register
         */
        R,
        
        // Additional register definitions useful for MDL (individual bytes of the registers above):
        AF,
        AF_ALT,
        B,
        C,
        D,
        E,
        H,
        L,
        B_ALT,
        C_ALT,
        D_ALT,
        E_ALT,
        H_ALT,
        L_ALT,
        IXH,
        IXL,
        IYH,
        IYL,
    }
    // final static int msw = 0xFFFF0000;

    
    // If you pass in an 8 bit register, it will be returned as is, but if
    // you pass in a 16 bit register, you will get the two 8 bit registers that make
    // up the 16 bit one as a result:
    public static RegisterNames[] primitive8BitRegistersOf(RegisterNames reg)
    {
        switch(reg) {
            case AF:
                return new RegisterNames[]{RegisterNames.A, RegisterNames.F};
            case BC:
                return new RegisterNames[]{RegisterNames.B, RegisterNames.C};
            case DE:
                return new RegisterNames[]{RegisterNames.D, RegisterNames.E};
            case HL:
                return new RegisterNames[]{RegisterNames.H, RegisterNames.L};
            case AF_ALT:
                return new RegisterNames[]{RegisterNames.A_ALT, RegisterNames.F_ALT};
            case BC_ALT:
                return new RegisterNames[]{RegisterNames.B_ALT, RegisterNames.C_ALT};
            case DE_ALT:
                return new RegisterNames[]{RegisterNames.D_ALT, RegisterNames.E_ALT};
            case HL_ALT:
                return new RegisterNames[]{RegisterNames.H_ALT, RegisterNames.L_ALT};
            case IX:
                return new RegisterNames[]{RegisterNames.IXH, RegisterNames.IXL};
            case IY:
                return new RegisterNames[]{RegisterNames.IYH, RegisterNames.IYL};
            default:
                return new RegisterNames[]{reg};
        }
    }

    
    public static RegisterNames[] ghost8BitRegistersOf(RegisterNames reg)
    {
        switch(reg) {
            case AF_ALT:
                return new RegisterNames[]{RegisterNames.A, RegisterNames.F};
            case BC_ALT:
                return new RegisterNames[]{RegisterNames.B, RegisterNames.C};
            case DE_ALT:
                return new RegisterNames[]{RegisterNames.D, RegisterNames.E};
            case HL_ALT:
                return new RegisterNames[]{RegisterNames.H, RegisterNames.L};
            case AF:
                return new RegisterNames[]{RegisterNames.A_ALT, RegisterNames.F_ALT};
            case BC:
                return new RegisterNames[]{RegisterNames.B_ALT, RegisterNames.C_ALT};
            case DE:
                return new RegisterNames[]{RegisterNames.D_ALT, RegisterNames.E_ALT};
            case HL:
                return new RegisterNames[]{RegisterNames.H_ALT, RegisterNames.L_ALT};
            case A:
                return new RegisterNames[]{RegisterNames.A_ALT};
            case F:
                return new RegisterNames[]{RegisterNames.F_ALT};
            case B:
                return new RegisterNames[]{RegisterNames.B_ALT};
            case C:
                return new RegisterNames[]{RegisterNames.C_ALT};
            case D:
                return new RegisterNames[]{RegisterNames.D_ALT};
            case E:
                return new RegisterNames[]{RegisterNames.E_ALT};
            case H:
                return new RegisterNames[]{RegisterNames.H_ALT};
            case L:
                return new RegisterNames[]{RegisterNames.L_ALT};
            case A_ALT:
                return new RegisterNames[]{RegisterNames.A};
            case F_ALT:
                return new RegisterNames[]{RegisterNames.F};
            case B_ALT:
                return new RegisterNames[]{RegisterNames.B};
            case C_ALT:
                return new RegisterNames[]{RegisterNames.C};
            case D_ALT:
                return new RegisterNames[]{RegisterNames.D};
            case E_ALT:
                return new RegisterNames[]{RegisterNames.E};
            case H_ALT:
                return new RegisterNames[]{RegisterNames.H};
            case L_ALT:
                return new RegisterNames[]{RegisterNames.L};
            default:
                return new RegisterNames[]{};
        }
    }
    
    
    public static boolean is8bitRegister(RegisterNames reg)
    {
        switch(reg) {
            case A:
            case B:
            case C:
            case D:
            case E:
            case F:
            case H:
            case L:
            case I:
            case R:
            case A_ALT:
            case B_ALT:
            case C_ALT:
            case D_ALT:
            case E_ALT:
            case F_ALT:
            case H_ALT:
            case L_ALT:
            case IXH:
            case IXL:
            case IYH:
            case IYL:
                return true;
        }
        return false;
    }
    
    
    public static String registerName(RegisterNames reg)
    {
        switch(reg) {
            case BC: return "bc";
            case DE: return "de";
            case HL: return "hl";
            case BC_ALT: return "bc'";
            case DE_ALT: return "de'";
            case HL_ALT: return "hl'";
            case IX: return "ix";
            case IY: return "iy";
            case SP: return "sp";
            case PC: return "pc";
            case A: return "a";
            case F: return "f";
            case A_ALT: return "a'";
            case F_ALT: return "f'";
            case I: return "i";
            case R: return "r";

            case AF: return "af";
            case AF_ALT: return "af'";
            case B: return "b";
            case C: return "c";
            case D: return "d";
            case E: return "e";
            case H: return "h";
            case L: return "l";
            case B_ALT: return "b'";
            case C_ALT: return "c'";
            case D_ALT: return "d'";
            case E_ALT: return "e'";
            case H_ALT: return "h'";
            case L_ALT: return "l'";
            case IXH: return "ixh";
            case IXL: return "ixl";
            case IYH: return "iyh";
            case IYL: return "iyl";
            default:
                return "<unknown>";
        }
    }
    
    
    public static RegisterNames registerByName(String reg)
    {
        switch(reg.toLowerCase()) {
            case "bc": return RegisterNames.BC;
            case "de": return RegisterNames.DE;
            case "hl": return RegisterNames.HL;
            case "bc'": return RegisterNames.BC_ALT;
            case "de'": return RegisterNames.DE_ALT;
            case "hl'": return RegisterNames.HL_ALT;
            case "ix": return RegisterNames.IX;
            case "iy": return RegisterNames.IY;
            case "sp": return RegisterNames.SP;
            case "pc": return RegisterNames.PC;
            case "a": return RegisterNames.A;
            case "f": return RegisterNames.F;
            case "a'": return RegisterNames.A_ALT;
            case "f'": return RegisterNames.F_ALT;
            case "i": return RegisterNames.I;
            case "r": return RegisterNames.R;
            case "af": return RegisterNames.AF;
            case "af'": return RegisterNames.AF_ALT;
            case "b": return RegisterNames.B;
            case "c": return RegisterNames.C;
            case "d": return RegisterNames.D;
            case "e": return RegisterNames.E;
            case "h": return RegisterNames.H;
            case "l": return RegisterNames.L;
            case "b'": return RegisterNames.B_ALT;
            case "c'": return RegisterNames.C_ALT;
            case "d'": return RegisterNames.D_ALT;
            case "e'": return RegisterNames.E_ALT;
            case "h'": return RegisterNames.H_ALT;
            case "l'": return RegisterNames.L_ALT;
            case "ixh": return RegisterNames.IXH;
            case "ixl": return RegisterNames.IXL;
            case "iyh": return RegisterNames.IYH;
            case "iyl": return RegisterNames.IYL;
            default:
                return null;
        }    
    }

}

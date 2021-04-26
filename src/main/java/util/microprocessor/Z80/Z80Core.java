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

import util.microprocessor.*;
import util.microprocessor.Z80.CPUConstants.*;

import static util.microprocessor.Z80.CPUConstants.*;

/**
 * The ZiLOG Z80 processor core
 */
public class Z80Core implements ICPUData {

    // maximum address size
    private final static int MAX_ADDRESS = 65535;
    private final IMemory ram;
    private final IBaseDevice io;
    private final CPUConfig config;

    private int instruction;
    private boolean halt;
    private long tStates;
    /* registers */
    private int reg_B, reg_C, reg_D, reg_E, reg_H, reg_L;
    private int reg_B_ALT, reg_C_ALT, reg_D_ALT, reg_E_ALT, reg_H_ALT, reg_L_ALT;
    private int reg_IX, reg_IY, reg_PC, reg_SP;
    private int reg_A, reg_A_ALT, reg_F, reg_F_ALT, reg_I, reg_R;
    private int reg_index;
    private boolean EIDIFlag;
    private boolean IFF1, IFF2;
    private boolean NMI_FF;
    private boolean blockMove;
    private int resetAddress;

    /**
     * Standard constructor. Set the processor up with a memory and I/O interface.
     *
     * @param ram Interface to the memory architecture
     * @param io  Interface to the i/o port architecture
     * @param config specific constants for the CPU that wants to be simulated
     */
    public Z80Core(IMemory ram, IBaseDevice io, CPUConfig config) {
        this.ram = ram;
        this.io = io;
        this.config = config;
        tStates = 0;

        blockMove = false;
        resetAddress = 0x0000;
    }
    

    /*
     * Public interfaces to processor control functions
     */

    /**
     * Indicate when a block move instruction is in progress, LDIR, CPDR etc. May be sampled during repetitive cycles of
     * the instruction
     *
     * @return true represents a block move, else false if not executing
     */
    public boolean blockMoveInProgress() {
        return blockMove;
    }

    /**
     * Reset the processor to a known state. Equivalent to a hardware reset.
     */
    private void processorReset() {
        halt = false;

        reg_B = reg_C = reg_D = reg_E = reg_H = reg_L = 0;
        reg_B_ALT = reg_C_ALT = reg_D_ALT = reg_E_ALT = reg_H_ALT = reg_L_ALT = 0;
        reg_IX = reg_IY = reg_SP = 0;
        reg_A = reg_A_ALT = reg_F = reg_F_ALT = reg_I = reg_R = 0;
        IFF1 = IFF2 = false;
        EIDIFlag = false;
        NMI_FF = false;

        reg_PC = resetAddress;

        tStates = 0;
    }

    /**
     * Reset the processor to a known state. Equivalent to a hardware reset.
     */
    public void reset() {
        processorReset();
    }

    /**
     * Quick reset when the status of the registers does not matter.
     */
    public final void shallowReset() {
        halt = false;
        reg_PC = resetAddress;
        tStates = 0;
    }    
    
    /**
     * Initiate an NMI request
     */
    public void setNMI() {
        NMI_FF = true;
    }

    /**
     * Returns the state of the halt flag
     *
     * @return True if the processor has executed a HALT instruction
     */
    public boolean getHalt() {
        return halt;
    }

    /**
     * Recover the present program counter (PC) value
     *
     * @return Value in the range 0x0000 to 0xFFFF
     */
    public int getProgramCounter() {
        return reg_PC;
    }

    /**
     * Force load the program counter (PC)
     *
     * @param pc Value in the range 0x0000 to 0xFFFF
     */
    public void setProgramCounter(int pc) {
        reg_PC = pc;
    }

    /**
     * Set the reset program address. This is the address the processor will fetch its first instruction from when
     * reset.
     *
     * @param address Value in the range 0x0000 to 0xFFFF
     */
    public void setResetAddress(int address) {
        resetAddress = address;
    }

    /**
     * Recover a register value via a register name
     *
     * @param name Register name
     * @return The register value
     */
    public int getRegisterValue(RegisterNames name) {
        switch (name) {
            case BC:
                return getBC();
            case DE:
                return getDE();
            case HL:
                return getHL();
            case BC_ALT:
                return getBC_ALT();
            case DE_ALT:
                return getDE_ALT();
            case HL_ALT:
                return getHL_ALT();
            case IX:
                return reg_IX;
            case IY:
                return reg_IY;
            case SP:
                return getSP();
            case PC:
                return reg_PC;
            case A:
                return reg_A;
            case F:
                return reg_F;
            case A_ALT:
                return reg_A_ALT;
            case F_ALT:
                return reg_F_ALT;
            case I:
                return reg_I;
            case R:
                return reg_R;
                
            // Additional register parts, useful for MDL:
            case B:
                return reg_B;
            case C:
                return reg_C;
            case D:
                return reg_D;
            case E:
                return reg_E;
            case H:
                return reg_H;
            case L:
                return reg_L;
            case IXH:
                return reg_IX >> 8;
            case IXL:
                return reg_IX & 0xff;
            case IYH:
                return reg_IY >> 8;
//            case IYL:
            default:
                return reg_IY & 0xff;
        }
    }
    
    
    /**
     * Sets a register value via a register name
     *
     * @param name Register name
     * @return The register value
     */
    public void setRegisterValue(RegisterNames name, int value) {
        switch (name) {
            case BC:
                setBC(value & 0xffff);
                break;
            case DE:
                setDE(value & 0xffff);
                break;
            case HL:
                setHL(value & 0xffff);
                break;
            case BC_ALT:
                setBC_ALT(value & 0xffff);
                break;
            case DE_ALT:
                setDE_ALT(value & 0xffff);
                break;
            case HL_ALT:
                setHL_ALT(value & 0xffff);
                break;
            case IX:
                reg_IX = value & 0xffff;
                break;
            case IY:
                reg_IY = value & 0xffff;
                break;
            case SP:
                reg_SP = value & 0xffff;
                break;
            case PC:
                reg_PC = value & 0xffff;
                break;
            case A:
                reg_A = value & 0xff;
                break;
            case F:
                reg_F = value & 0xff;
                break;
            case A_ALT:
                reg_A_ALT = value & 0xff;
                break;
            case F_ALT:
                reg_F_ALT = value & 0xff;
                break;
            case I:
                reg_I = value & 0xff;
                break;
            case R:
                reg_R = value & 0xff;
                break;                
            case B:
                reg_B = value & 0xff;
            case C:
                reg_C = value & 0xff;
                break;
            case D:
                reg_D = value & 0xff;
                break;
            case E:
                reg_E = value & 0xff;
                break;
            case H:
                reg_H = value & 0xff;
                break;
            case L:
                reg_L = value & 0xff;
                break;
            case IXH:
                reg_IX = ((value & 0xff) << 8) + (reg_IX & 0xff);
                break;
            case IXL:
                reg_IX = (value & 0xff) + (reg_IX & 0xff00);
                break;
            case IYH:
                reg_IY = ((value & 0xff) << 8) + (reg_IY & 0xff);
                break;
//            case IYL:
            default:
                reg_IY = (value & 0xff) + (reg_IY & 0xff00);
                break;
        }
    }    

    /**
     * Get the present Stack Pointer value
     *
     * @return Returns the SP
     */
    public int getSP() {
        return reg_SP;
    }

    /**
     * Execute a single instruction at the present program counter (PC) then return. The internal state of the processor
     * is updated along with the T state count.
     *
     * @throws ProcessorException Thrown if an unexpected state arises
     */
    public void executeOneInstruction() throws ProcessorException {

        // NMI check first
        if (NMI_FF) {
            // can't interrupt straight after an EI or DI
            if (EIDIFlag) {
                EIDIFlag = false;
            } else {
                NMI_FF = false; // interrupt accepted
                IFF2 = IFF1; // store IFF state
                dec2SP();
                if (halt) {
                    incPC(); // Was a bug ! - point to instruction after(!) interrupt location. HALT decrements PC !!!
                }
                ram.writeWord(reg_SP, reg_PC);
                reg_PC = 0x0066; // NMI routine location
            }
        }
        halt = false;
        instruction = ram.readByte(reg_PC);
        incPC();
        try {
            decodeOneByteInstruction(instruction);
        } catch (ProcessorException e) {
            decPC();
            throw e;
        }
    }

    /**
     * Return the number of T states since last reset
     *
     * @return Processor T states
     */
    public long getTStates() {
        return tStates;
    }

    /**
     * Reset the T state counter to zero
     */
    public void resetTStates() {
        tStates = 0;
    }

    /**
     * Execute all one byte instructions and pass multi-byte instructions on for further processing
     *
     * @param opcode Instruction byte
     * @throws ProcessorException Thrown if anything goes wrong
     */
    private void decodeOneByteInstruction(int opcode) throws ProcessorException {
        tStates = tStates + config.OPCODE_T_STATES[opcode];
        switch (opcode) {
            case 0x00: {
                break;
            } // null
            case 0x01: {
                setBC(ram.readWord(reg_PC));
                inc2PC();
                break;
            } // LD bc, nnnn
            case 0x02: {
                ram.writeByte(getBC(), reg_A);
                break;
            } // LD (BC), A
            case 0x03: {
                setBC(ALU16BitInc(getBC()));
                break;
            } // inc BC
            case 0x04: {
                reg_B = ALU8BitInc(reg_B);
                break;
            } // inc b
            case 0x05: {
                reg_B = ALU8BitDec(reg_B);
                break;
            } // dec b
            case 0x06: {
                reg_B = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld b,nn
            case 0x07: {
                RLCA();
                break;
            } // rlca
            case 0x08: {
                EXAFAF();
                break;
            } // ex af,af'
            case 0x09: {
                setHL(ALU16BitAdd(getBC()));
                break;
            } // add hl,bc
            case 0x0A: {
                reg_A = ram.readByte(getBC());
                break;
            } // LD a, (bc)
            case 0x0B: {
                setBC(ALU16BitDec(getBC()));
                break;
            } // dec bc
            case 0x0C: {
                reg_C = ALU8BitInc(reg_C);
                break;
            } // inc c
            case 0x0D: {
                reg_C = ALU8BitDec(reg_C);
                break;
            } // dec c
            case 0x0E: {
                reg_C = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld c,n
            case 0x0F: {
                RRCA();
                break;
            } // rrca

            case 0x10: {
                djnz();
                break;
            } // djnz
            case 0x11: {
                setDE(ram.readWord(reg_PC));
                inc2PC();
                break;
            } // LD de, nnnn
            case 0x12: {
                ram.writeByte(getDE(), reg_A);
                break;
            } // LD (de), A
            case 0x13: {
                setDE(ALU16BitInc(getDE()));
                break;
            } // inc de
            case 0x14: {
                reg_D = ALU8BitInc(reg_D);
                break;
            } // inc d
            case 0x15: {
                reg_D = ALU8BitDec(reg_D);
                break;
            } // dec d
            case 0x16: {
                reg_D = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld d,nn
            case 0x17: {
                RLA();
                break;
            } // rla
            case 0x18: {
                relativeJump();
                break;
            } // jr
            case 0x19: {
                setHL(ALU16BitAdd(getDE()));
                break;
            } // add hl,de
            case 0x1A: {
                reg_A = ram.readByte(getDE());
                break;
            } // LD a, (de)
            case 0x1B: {
                setDE(ALU16BitDec(getDE()));
                break;
            } // dec de
            case 0x1C: {
                reg_E = ALU8BitInc(reg_E);
                break;
            } // inc e
            case 0x1D: {
                reg_E = ALU8BitDec(reg_E);
                break;
            } // dec e
            case 0x1E: {
                reg_E = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld e,n
            case 0x1F: {
                RRA();
                break;
            } // rra

            case 0x20: {
                if (!getZ()) {
                    tStates = tStates + config.OPCODE_T_STATES2[opcode];
                    relativeJump();
                } else {
                    incPC();
                }
                break;
            } // jr nz
            case 0x21: {
                setHL(ram.readWord(reg_PC));
                inc2PC();
                break;
            } // LD hl, nnnn
            case 0x22: {
                ram.writeWord(ram.readWord(reg_PC), getHL());
                inc2PC();
                break;
            } // LD (nnnn), hl
            case 0x23: {
                setHL(ALU16BitInc(getHL()));
                break;
            } // inc hl
            case 0x24: {
                reg_H = ALU8BitInc(reg_H);
                break;
            } // inc h
            case 0x25: {
                reg_H = ALU8BitDec(reg_H);
                break;
            } // dec h
            case 0x26: {
                reg_H = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld h,nn
            case 0x27: {
                DAA();
                break;
            } // daa
            case 0x28: {
                if (getZ()) {
                    tStates = tStates + config.OPCODE_T_STATES2[opcode];
                    relativeJump();
                } else {
                    incPC();
                }
                break;
            } // jr z
            case 0x29: {
                setHL(ALU16BitAdd(getHL()));
                break;
            } // add hl,hl
            case 0x2A: {
                setHL(ram.readWord(ram.readWord(reg_PC)));
                inc2PC();
                break;
            } // LD hl, (nnnn)
            case 0x2B: {
                setHL(ALU16BitDec(getHL()));
                break;
            } // dec hl
            case 0x2C: {
                reg_L = ALU8BitInc(reg_L);
                break;
            } // inc l
            case 0x2D: {
                reg_L = ALU8BitDec(reg_L);
                break;
            } // dec l
            case 0x2E: {
                reg_L = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld l,n
            case 0x2F: {
                CPL();
                break;
            } // rra

            case 0x30: {
                if (!getC()) {
                    tStates = tStates + config.OPCODE_T_STATES2[opcode];
                    relativeJump();
                } else {
                    incPC();
                }
                break;
            } // jr nc
            case 0x31: {
                reg_SP = ram.readWord(reg_PC);
                inc2PC();
                break;
            } // LD sp, nnnn
            case 0x32: {
                ram.writeByte(ram.readWord(reg_PC), reg_A);
                inc2PC();
                break;
            } // LD (nnnn), A
            case 0x33: {
                reg_SP = ALU16BitInc(reg_SP);
                break;
            } // inc SP
            case 0x34: {
                ram.writeByte(getHL(), ALU8BitInc(ram.readByte(getHL())));
                break;
            } // inc (hl)
            case 0x35: {
                ram.writeByte(getHL(), ALU8BitDec(ram.readByte(getHL())));
                break;
            } // dec (hl)
            case 0x36: {
                ram.writeByte(getHL(), ram.readByte(reg_PC));
                incPC();
                break;
            } // ld (hl), nn
            case 0x37: {
                SCF();
                break;
            } // scf
            case 0x38: {
                if (getC()) {
                    tStates = tStates + config.OPCODE_T_STATES2[opcode];
                    relativeJump();
                } else {
                    incPC();
                }
                break;
            } // jr c
            case 0x39: {
                setHL(ALU16BitAdd(reg_SP));
                break;
            } // add hl,sp
            case 0x3A: {
                reg_A = ram.readByte(ram.readWord(reg_PC));
                inc2PC();
                break;
            } // LD a, (nnnn)
            case 0x3B: {
                reg_SP = ALU16BitDec(reg_SP);
                break;
            } // dec sp
            case 0x3C: {
                reg_A = ALU8BitInc(reg_A);
                break;
            } // inc a
            case 0x3D: {
                reg_A = ALU8BitDec(reg_A);
                break;
            } // dec a
            case 0x3E: {
                reg_A = ram.readByte(reg_PC);
                incPC();
                break;
            } // ld a,n
            case 0x3F: {
                CCF();
                break;
            } // ccf

            // LD B,*
            case 0x40: /* reg_B = reg_B; */
                break; // ld b,b
            case 0x41:
                reg_B = reg_C;
                break; // ld b,c
            case 0x42:
                reg_B = reg_D;
                break; // ld b,d
            case 0x43:
                reg_B = reg_E;
                break; // ld b,e
            case 0x44:
                reg_B = reg_H;
                break; // ld b,h
            case 0x45:
                reg_B = reg_L;
                break; // ld b,l
            case 0x46:
                reg_B = ram.readByte(getHL());
                break; // ld b,(hl)
            case 0x47:
                reg_B = reg_A;
                break; // ld b,a
            // LD C,*
            case 0x48:
                reg_C = reg_B;
                break; // ld c,b
            case 0x49: /* reg_C = reg_C; */
                break; // ld c,c
            case 0x4A:
                reg_C = reg_D;
                break; // ld c,d
            case 0x4B:
                reg_C = reg_E;
                break; // ld c,e
            case 0x4C:
                reg_C = reg_H;
                break; // ld c,h
            case 0x4D:
                reg_C = reg_L;
                break; // ld c,l
            case 0x4E:
                reg_C = ram.readByte(getHL());
                break; // ld c,(hl)
            case 0x4F:
                reg_C = reg_A;
                break; // ld c,a
            // LD D,*
            case 0x50:
                reg_D = reg_B;
                break; // ld d,b
            case 0x51:
                reg_D = reg_C;
                break; // ld d,c
            case 0x52: /* reg_D = reg_D; */
                break; // ld d,d
            case 0x53:
                reg_D = reg_E;
                break; // ld d,e
            case 0x54:
                reg_D = reg_H;
                break; // ld d,h
            case 0x55:
                reg_D = reg_L;
                break; // ld d,l
            case 0x56:
                reg_D = ram.readByte(getHL());
                break; // ld d,(hl)
            case 0x57:
                reg_D = reg_A;
                break; // ld d,a
            // LD E,*
            case 0x58:
                reg_E = reg_B;
                break; // ld e,b
            case 0x59:
                reg_E = reg_C;
                break; // ld e,c
            case 0x5A:
                reg_E = reg_D;
                break; // ld e,d
            case 0x5B: /* reg_E = reg_E; */
                break; // ld e,e
            case 0x5C:
                reg_E = reg_H;
                break; // ld e,h
            case 0x5D:
                reg_E = reg_L;
                break; // ld e,l
            case 0x5E:
                reg_E = ram.readByte(getHL());
                break; // ld e,(hl)
            case 0x5F:
                reg_E = reg_A;
                break; // ld e,a
            // LD H,*
            case 0x60:
                reg_H = reg_B;
                break; // ld h,b
            case 0x61:
                reg_H = reg_C;
                break; // ld h,c
            case 0x62:
                reg_H = reg_D;
                break; // ld h,d
            case 0x63:
                reg_H = reg_E;
                break; // ld h,e
            case 0x64: /* reg_H = reg_H; */
                break; // ld h,h
            case 0x65:
                reg_H = reg_L;
                break; // ld h,l
            case 0x66:
                reg_H = ram.readByte(getHL());
                break; // ld h,(hl)
            case 0x67:
                reg_H = reg_A;
                break; // ld h,a
            // LD L,*
            case 0x68:
                reg_L = reg_B;
                break; // ld l,b
            case 0x69:
                reg_L = reg_C;
                break; // ld l,c
            case 0x6A:
                reg_L = reg_D;
                break; // ld l,d
            case 0x6B:
                reg_L = reg_E;
                break; // ld l,e
            case 0x6C:
                reg_L = reg_H;
                break; // ld l,h
            case 0x6D: /* reg_L = reg_L; */
                break; // ld l,l
            case 0x6E:
                reg_L = ram.readByte(getHL());
                break; // ld l,(hl)
            case 0x6F:
                reg_L = reg_A;
                break; // ld l,a
            // LD (HL),*
            case 0x70:
                ram.writeByte(getHL(), reg_B);
                break; // ld (hl),b
            case 0x71:
                ram.writeByte(getHL(), reg_C);
                break; // ld (hl),c
            case 0x72:
                ram.writeByte(getHL(), reg_D);
                break; // ld (hl),d
            case 0x73:
                ram.writeByte(getHL(), reg_E);
                break; // ld (hl),e
            case 0x74:
                ram.writeByte(getHL(), reg_H);
                break; // ld (hl),h
            case 0x75:
                ram.writeByte(getHL(), reg_L);
                break; // ld (hl),l
            case 0x76: {
                decPC(); // execute it forever !
                halt = true;
                break;
            }
            case 0x77:
                ram.writeByte(getHL(), reg_A);
                break; // ld (hl),a
            // LD A,*
            case 0x78:
                reg_A = reg_B;
                break; // ld a,b
            case 0x79:
                reg_A = reg_C;
                break; // ld a,c
            case 0x7A:
                reg_A = reg_D;
                break; // ld a,d
            case 0x7B:
                reg_A = reg_E;
                break; // ld a,e
            case 0x7C:
                reg_A = reg_H;
                break; // ld a,h
            case 0x7D:
                reg_A = reg_L;
                break; // ld a,l
            case 0x7E:
                reg_A = ram.readByte(getHL());
                break; // ld a,(hl)
            case 0x7F: /* reg_A = reg_A; */
                break; // ld a,a
    
            case 0x80: {
                ALU8BitAdd(reg_B);
                break;
            }
            case 0x81: {
                ALU8BitAdd(reg_C);
                break;
            }
            case 0x82: {
                ALU8BitAdd(reg_D);
                break;
            }
            case 0x83: {
                ALU8BitAdd(reg_E);
                break;
            }
            case 0x84: {
                ALU8BitAdd(reg_H);
                break;
            }
            case 0x85: {
                ALU8BitAdd(reg_L);
                break;
            }
            case 0x86: {
                ALU8BitAdd(ram.readByte(getHL()));
                break;
            }
            case 0x87: {
                ALU8BitAdd(reg_A);
                break;
            }
            case 0x88: {
                ALU8BitAdc(reg_B);
                break;
            }
            case 0x89: {
                ALU8BitAdc(reg_C);
                break;
            }
            case 0x8A: {
                ALU8BitAdc(reg_D);
                break;
            }
            case 0x8B: {
                ALU8BitAdc(reg_E);
                break;
            }
            case 0x8C: {
                ALU8BitAdc(reg_H);
                break;
            }
            case 0x8D: {
                ALU8BitAdc(reg_L);
                break;
            }
            case 0x8E: {
                ALU8BitAdc(ram.readByte(getHL()));
                break;
            }
            case 0x8F: {
                ALU8BitAdc(reg_A);
                break;
            }
    
            case 0x90: {
                ALU8BitSub(reg_B);
                break;
            }
            case 0x91: {
                ALU8BitSub(reg_C);
                break;
            }
            case 0x92: {
                ALU8BitSub(reg_D);
                break;
            }
            case 0x93: {
                ALU8BitSub(reg_E);
                break;
            }
            case 0x94: {
                ALU8BitSub(reg_H);
                break;
            }
            case 0x95: {
                ALU8BitSub(reg_L);
                break;
            }
            case 0x96: {
                ALU8BitSub(ram.readByte(getHL()));
                break;
            }
            case 0x97: {
                ALU8BitSub(reg_A);
                break;
            }
            case 0x98: {
                ALU8BitSbc(reg_B);
                break;
            }
            case 0x99: {
                ALU8BitSbc(reg_C);
                break;
            }
            case 0x9A: {
                ALU8BitSbc(reg_D);
                break;
            }
            case 0x9B: {
                ALU8BitSbc(reg_E);
                break;
            }
            case 0x9C: {
                ALU8BitSbc(reg_H);
                break;
            }
            case 0x9D: {
                ALU8BitSbc(reg_L);
                break;
            }
            case 0x9E: {
                ALU8BitSbc(ram.readByte(getHL()));
                break;
            }
            case 0x9F: {
                ALU8BitSbc(reg_A);
                break;
            }
    
            case 0xA0: {
                ALU8BitAnd(reg_B);
                break;
            }
            case 0xA1: {
                ALU8BitAnd(reg_C);
                break;
            }
            case 0xA2: {
                ALU8BitAnd(reg_D);
                break;
            }
            case 0xA3: {
                ALU8BitAnd(reg_E);
                break;
            }
            case 0xA4: {
                ALU8BitAnd(reg_H);
                break;
            }
            case 0xA5: {
                ALU8BitAnd(reg_L);
                break;
            }
            case 0xA6: {
                ALU8BitAnd(ram.readByte(getHL()));
                break;
            }
            case 0xA7: {
                ALU8BitAnd(reg_A);
                break;
            }
            case 0xA8: {
                ALU8BitXor(reg_B);
                break;
            }
            case 0xA9: {
                ALU8BitXor(reg_C);
                break;
            }
            case 0xAA: {
                ALU8BitXor(reg_D);
                break;
            }
            case 0xAB: {
                ALU8BitXor(reg_E);
                break;
            }
            case 0xAC: {
                ALU8BitXor(reg_H);
                break;
            }
            case 0xAD: {
                ALU8BitXor(reg_L);
                break;
            }
            case 0xAE: {
                ALU8BitXor(ram.readByte(getHL()));
                break;
            }
            case 0xAF: {
                ALU8BitXor(reg_A);
                break;
            }
    
            case 0xB0: {
                ALU8BitOr(reg_B);
                break;
            }
            case 0xB1: {
                ALU8BitOr(reg_C);
                break;
            }
            case 0xB2: {
                ALU8BitOr(reg_D);
                break;
            }
            case 0xB3: {
                ALU8BitOr(reg_E);
                break;
            }
            case 0xB4: {
                ALU8BitOr(reg_H);
                break;
            }
            case 0xB5: {
                ALU8BitOr(reg_L);
                break;
            }
            case 0xB6: {
                ALU8BitOr(ram.readByte(getHL()));
                break;
            }
            case 0xB7: {
                ALU8BitOr(reg_A);
                break;
            }
            case 0xB8: {
                ALU8BitCp(reg_B);
                break;
            }
            case 0xB9: {
                ALU8BitCp(reg_C);
                break;
            }
            case 0xBA: {
                ALU8BitCp(reg_D);
                break;
            }
            case 0xBB: {
                ALU8BitCp(reg_E);
                break;
            }
            case 0xBC: {
                ALU8BitCp(reg_H);
                break;
            }
            case 0xBD: {
                ALU8BitCp(reg_L);
                break;
            }
            case 0xBE: {
                ALU8BitCp(ram.readByte(getHL()));
                break;
            }
            case 0xBF: {
                ALU8BitCp(reg_A);
                break;
            }
    
            case 0xC0: {
                ret(!getZ(), 0xC0);
                break;
            }
            case 0xC1: {
                setBC(ram.readWord(reg_SP));
                inc2SP();
                break;
            }
            case 0xC2: {
                jp(!getZ());
                break;
            }
            case 0xC3: {
                jp();
                break;
            }
            case 0xC4: {
                call(!getZ(), 0xC4);
                break;
            }
            case 0xC5: {
                dec2SP();
                ram.writeWord(reg_SP, getBC());
                break;
            }
            case 0xC6: {
                ALU8BitAdd(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xc7: {
                rst(0);
                break;
            }
            case 0xC8: {
                ret(getZ(), 0xC8);
                break;
            }
            case 0xC9: {
                ret();
                break;
            }
            case 0xCA: {
                jp(getZ());
                break;
            }
            case 0xCB: {
                extendedCB();
                break;
            }
            case 0xCC: {
                call(getZ(), 0xCC);
                break;
            }
            case 0xCD: {
                call();
                break;
            }
            case 0xCE: {
                ALU8BitAdc(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xCF: {
                rst(1);
                break;
            }
    
            case 0xD0: {
                ret(!getC(), 0xD0);
                break;
            }
            case 0xD1: {
                setDE(ram.readWord(reg_SP));
                inc2SP();
                break;
            }
            case 0xD2: {
                jp(!getC());
                break;
            }
            case 0xD3: {
                outNA();
                break;
            }
            case 0xD4: {
                call(!getC(), 0xD4);
                break;
            }
            case 0xD5: {
                dec2SP();
                ram.writeWord(reg_SP, getDE());
                break;
            }
            case 0xD6: {
                ALU8BitSub(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xD7: {
                rst(2);
                break;
            }
            case 0xD8: {
                ret(getC(), 0xD8);
                break;
            }
            case 0xD9: {
                EXX();
                break;
            }
            case 0xDA: {
                jp(getC());
                break;
            }
            case 0xDB: {
                inAN();
                break;
            }
            case 0xDC: {
                call(getC(), 0xDC);
                break;
            }
            case 0xDD: {
                extendedDD();
                break;
            }
            case 0xDE: {
                ALU8BitSbc(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xDF: {
                rst(3);
                break;
            }
    
            case 0xE0: {
                ret(!getPV(), 0xE0);
                break;
            }
            case 0xE1: {
                setHL(ram.readWord(reg_SP));
                inc2SP();
                break;
            }
            case 0xE2: {
                jp(!getPV());
                break;
            }
            case 0xE3: {
                EXSPHL();
                break;
            }
            case 0xE4: {
                call(!getPV(), 0xE4);
                break;
            }
            case 0xE5: {
                dec2SP();
                ram.writeWord(reg_SP, getHL());
                break;
            }
            case 0xE6: {
                ALU8BitAnd(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xE7: {
                rst(4);
                break;
            }
            case 0xE8: {
                ret(getPV(), 0xE8);
                break;
            }
            case 0xE9: {
                reg_PC = getHL();
                break;
            }
            case 0xEA: {
                jp(getPV());
                break;
            }
            case 0xEB: {
                EXDEHL();
                break;
            }
            case 0xEC: {
                call(getPV(), 0xEC);
                break;
            }
            case 0xED: {
                extendedED();
                break;
            }
            case 0xEE: {
                ALU8BitXor(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xEF: {
                rst(5);
                break;
            }
    
            case 0xF0: {
                ret(!getS(), 0xF0);
                break;
            }
            case 0xF1: {
                int temp = ram.readWord(reg_SP);
                inc2SP();
                reg_F = (temp & lsb);
                reg_A = ((temp & msb) >> 8);
                break;
            }
            case 0xF2: {
                jp(!getS());
                break;
            }
            case 0xF3: {
                DI();
                break;
            }
            case 0xF4: {
                call(!getS(), 0xF4);
                break;
            }
            case 0xF5: {
                dec2SP();
                ram.writeWord(reg_SP, (reg_A << 8) | reg_F);
                break;
            }
            case 0xF6: {
                ALU8BitOr(ram.readByte(reg_PC));
                incPC();
                break;
            }
            case 0xF7: {
                rst(6);
                break;
            }
            case 0xF8: {
                ret(getS(), 0xF8);
                break;
            }
            case 0xF9: {
                reg_SP = getHL();
                break;
            }
            case 0xFA: {
                jp(getS());
                break;
            }
            case 0xFB: {
                EI();
                break;
            }
            case 0xFC: {
                call(getS(), 0xFC);
                break;
            }
            case 0xFD: {
                extendedFD();
                break;
            }
            case 0xFE: {
                ALU8BitCp(ram.readByte(reg_PC));
                incPC();
                break;
            }
            default: {
                rst(7);
                break;
            }
        }
    }

    /*
     * *****************************************************************************
     *
     * Extended Instruction area
     *
     * *****************************************************************************
     */
    /*
     * *****************************************************************************
     *
     * CB Bit twiddling and shifting instructions
     *
     * *****************************************************************************
     */
    private void extendedCB() {
        instruction = ram.readByte(reg_PC);
        incPC();
        tStates = tStates + config.OPCODE_CB_STATES[instruction];
        // decode stage
        switch (instruction) {
            case 0x00: {
                reg_B = shiftGenericRLC(reg_B);
                break;
            }
            case 0x01: {
                reg_C = shiftGenericRLC(reg_C);
                break;
            }
            case 0x02: {
                reg_D = shiftGenericRLC(reg_D);
                break;
            }
            case 0x03: {
                reg_E = shiftGenericRLC(reg_E);
                break;
            }
            case 0x04: {
                reg_H = shiftGenericRLC(reg_H);
                break;
            }
            case 0x05: {
                reg_L = shiftGenericRLC(reg_L);
                break;
            }
            case 0x06: {
                ram.writeByte(getHL(), shiftGenericRLC(ram.readByte(getHL())));
                break;
            }
            case 0x07: {
                reg_A = shiftGenericRLC(reg_A);
                break;
            }
            case 0x08: {
                reg_B = shiftGenericRRC(reg_B);
                break;
            }
            case 0x09: {
                reg_C = shiftGenericRRC(reg_C);
                break;
            }
            case 0x0A: {
                reg_D = shiftGenericRRC(reg_D);
                break;
            }
            case 0x0B: {
                reg_E = shiftGenericRRC(reg_E);
                break;
            }
            case 0x0C: {
                reg_H = shiftGenericRRC(reg_H);
                break;
            }
            case 0x0D: {
                reg_L = shiftGenericRRC(reg_L);
                break;
            }
            case 0x0E: {
                ram.writeByte(getHL(), shiftGenericRRC(ram.readByte(getHL())));
                break;
            }
            case 0x0F: {
                reg_A = shiftGenericRRC(reg_A);
                break;
            }
            //
            case 0x10: {
                reg_B = shiftGenericRL(reg_B);
                break;
            }
            case 0x11: {
                reg_C = shiftGenericRL(reg_C);
                break;
            }
            case 0x12: {
                reg_D = shiftGenericRL(reg_D);
                break;
            }
            case 0x13: {
                reg_E = shiftGenericRL(reg_E);
                break;
            }
            case 0x14: {
                reg_H = shiftGenericRL(reg_H);
                break;
            }
            case 0x15: {
                reg_L = shiftGenericRL(reg_L);
                break;
            }
            case 0x16: {
                ram.writeByte(getHL(), shiftGenericRL(ram.readByte(getHL())));
                break;
            }
            case 0x17: {
                reg_A = shiftGenericRL(reg_A);
                break;
            }
            case 0x18: {
                reg_B = shiftGenericRR(reg_B);
                break;
            }
            case 0x19: {
                reg_C = shiftGenericRR(reg_C);
                break;
            }
            case 0x1A: {
                reg_D = shiftGenericRR(reg_D);
                break;
            }
            case 0x1B: {
                reg_E = shiftGenericRR(reg_E);
                break;
            }
            case 0x1C: {
                reg_H = shiftGenericRR(reg_H);
                break;
            }
            case 0x1D: {
                reg_L = shiftGenericRR(reg_L);
                break;
            }
            case 0x1E: {
                ram.writeByte(getHL(), shiftGenericRR(ram.readByte(getHL())));
                break;
            }
            case 0x1F: {
                reg_A = shiftGenericRR(reg_A);
                break;
            }
            //
            case 0x20: {
                reg_B = shiftGenericSLA(reg_B);
                break;
            }
            case 0x21: {
                reg_C = shiftGenericSLA(reg_C);
                break;
            }
            case 0x22: {
                reg_D = shiftGenericSLA(reg_D);
                break;
            }
            case 0x23: {
                reg_E = shiftGenericSLA(reg_E);
                break;
            }
            case 0x24: {
                reg_H = shiftGenericSLA(reg_H);
                break;
            }
            case 0x25: {
                reg_L = shiftGenericSLA(reg_L);
                break;
            }
            case 0x26: {
                ram.writeByte(getHL(), shiftGenericSLA(ram.readByte(getHL())));
                break;
            }
            case 0x27: {
                reg_A = shiftGenericSLA(reg_A);
                break;
            }
            case 0x28: {
                reg_B = shiftGenericSRA(reg_B);
                break;
            }
            case 0x29: {
                reg_C = shiftGenericSRA(reg_C);
                break;
            }
            case 0x2A: {
                reg_D = shiftGenericSRA(reg_D);
                break;
            }
            case 0x2B: {
                reg_E = shiftGenericSRA(reg_E);
                break;
            }
            case 0x2C: {
                reg_H = shiftGenericSRA(reg_H);
                break;
            }
            case 0x2D: {
                reg_L = shiftGenericSRA(reg_L);
                break;
            }
            case 0x2E: {
                ram.writeByte(getHL(), shiftGenericSRA(ram.readByte(getHL())));
                break;
            }
            case 0x2F: {
                reg_A = shiftGenericSRA(reg_A);
                break;
            }
            //
            // Undocumented SLL [0x30 to 0x37]. Instruction faulty, feeds in 1 to bit 0
            case 0x30: {
                reg_B = shiftGenericSLL(reg_B);
                break;
            }
            case 0x31: {
                reg_C = shiftGenericSLL(reg_C);
                break;
            }
            case 0x32: {
                reg_D = shiftGenericSLL(reg_D);
                break;
            }
            case 0x33: {
                reg_E = shiftGenericSLL(reg_E);
                break;
            }
            case 0x34: {
                reg_H = shiftGenericSLL(reg_H);
                break;
            }
            case 0x35: {
                reg_L = shiftGenericSLL(reg_L);
                break;
            }
            case 0x36: {
                ram.writeByte(getHL(), shiftGenericSLL(ram.readByte(getHL())));
                break;
            }
            case 0x37: {
                reg_A = shiftGenericSLL(reg_A);
                break;
            }
            //
            case 0x38: {
                reg_B = shiftGenericSRL(reg_B);
                break;
            }
            case 0x39: {
                reg_C = shiftGenericSRL(reg_C);
                break;
            }
            case 0x3A: {
                reg_D = shiftGenericSRL(reg_D);
                break;
            }
            case 0x3B: {
                reg_E = shiftGenericSRL(reg_E);
                break;
            }
            case 0x3C: {
                reg_H = shiftGenericSRL(reg_H);
                break;
            }
            case 0x3D: {
                reg_L = shiftGenericSRL(reg_L);
                break;
            }
            case 0x3E: {
                ram.writeByte(getHL(), shiftGenericSRL(ram.readByte(getHL())));
                break;
            }
            case 0x3F: {
                reg_A = shiftGenericSRL(reg_A);
                break;
            }
            //
            case 0x40: {
                testBit(reg_B, 0);
                break;
            }
            case 0x41: {
                testBit(reg_C, 0);
                break;
            }
            case 0x42: {
                testBit(reg_D, 0);
                break;
            }
            case 0x43: {
                testBit(reg_E, 0);
                break;
            }
            case 0x44: {
                testBit(reg_H, 0);
                break;
            }
            case 0x45: {
                testBit(reg_L, 0);
                break;
            }
            case 0x46: {
                testBitInMemory(0);
                break;
            }
            case 0x47: {
                testBit(reg_A, 0);
                break;
            }
            case 0x48: {
                testBit(reg_B, 1);
                break;
            }
            case 0x49: {
                testBit(reg_C, 1);
                break;
            }
            case 0x4A: {
                testBit(reg_D, 1);
                break;
            }
            case 0x4B: {
                testBit(reg_E, 1);
                break;
            }
            case 0x4C: {
                testBit(reg_H, 1);
                break;
            }
            case 0x4D: {
                testBit(reg_L, 1);
                break;
            }
            case 0x4E: {
                testBitInMemory(1);
                break;
            }
            case 0x4F: {
                testBit(reg_A, 1);
                break;
            }
            //
            case 0x50: {
                testBit(reg_B, 2);
                break;
            }
            case 0x51: {
                testBit(reg_C, 2);
                break;
            }
            case 0x52: {
                testBit(reg_D, 2);
                break;
            }
            case 0x53: {
                testBit(reg_E, 2);
                break;
            }
            case 0x54: {
                testBit(reg_H, 2);
                break;
            }
            case 0x55: {
                testBit(reg_L, 2);
                break;
            }
            case 0x56: {
                testBitInMemory(2);
                break;
            }
            case 0x57: {
                testBit(reg_A, 2);
                break;
            }
            case 0x58: {
                testBit(reg_B, 3);
                break;
            }
            case 0x59: {
                testBit(reg_C, 3);
                break;
            }
            case 0x5A: {
                testBit(reg_D, 3);
                break;
            }
            case 0x5B: {
                testBit(reg_E, 3);
                break;
            }
            case 0x5C: {
                testBit(reg_H, 3);
                break;
            }
            case 0x5D: {
                testBit(reg_L, 3);
                break;
            }
            case 0x5E: {
                testBitInMemory(3);
                break;
            }
            case 0x5F: {
                testBit(reg_A, 3);
                break;
            }
            //
            case 0x60: {
                testBit(reg_B, 4);
                break;
            }
            case 0x61: {
                testBit(reg_C, 4);
                break;
            }
            case 0x62: {
                testBit(reg_D, 4);
                break;
            }
            case 0x63: {
                testBit(reg_E, 4);
                break;
            }
            case 0x64: {
                testBit(reg_H, 4);
                break;
            }
            case 0x65: {
                testBit(reg_L, 4);
                break;
            }
            case 0x66: {
                testBitInMemory(4);
                break;
            }
            case 0x67: {
                testBit(reg_A, 4);
                break;
            }
            case 0x68: {
                testBit(reg_B, 5);
                break;
            }
            case 0x69: {
                testBit(reg_C, 5);
                break;
            }
            case 0x6A: {
                testBit(reg_D, 5);
                break;
            }
            case 0x6B: {
                testBit(reg_E, 5);
                break;
            }
            case 0x6C: {
                testBit(reg_H, 5);
                break;
            }
            case 0x6D: {
                testBit(reg_L, 5);
                break;
            }
            case 0x6E: {
                testBitInMemory(5);
                break;
            }
            case 0x6F: {
                testBit(reg_A, 5);
                break;
            }
            //
            case 0x70: {
                testBit(reg_B, 6);
                break;
            }
            case 0x71: {
                testBit(reg_C, 6);
                break;
            }
            case 0x72: {
                testBit(reg_D, 6);
                break;
            }
            case 0x73: {
                testBit(reg_E, 6);
                break;
            }
            case 0x74: {
                testBit(reg_H, 6);
                break;
            }
            case 0x75: {
                testBit(reg_L, 6);
                break;
            }
            case 0x76: {
                testBitInMemory(6);
                break;
            }
            case 0x77: {
                testBit(reg_A, 6);
                break;
            }
            case 0x78: {
                testBit(reg_B, 7);
                break;
            }
            case 0x79: {
                testBit(reg_C, 7);
                break;
            }
            case 0x7A: {
                testBit(reg_D, 7);
                break;
            }
            case 0x7B: {
                testBit(reg_E, 7);
                break;
            }
            case 0x7C: {
                testBit(reg_H, 7);
                break;
            }
            case 0x7D: {
                testBit(reg_L, 7);
                break;
            }
            case 0x7E: {
                testBitInMemory(7);
                break;
            }
            case 0x7F: {
                testBit(reg_A, 7);
                break;
            }
            //
            case 0x80: {
                reg_B = reg_B & resetBit0;
                break;
            }
            case 0x81: {
                reg_C = reg_C & resetBit0;
                break;
            }
            case 0x82: {
                reg_D = reg_D & resetBit0;
                break;
            }
            case 0x83: {
                reg_E = reg_E & resetBit0;
                break;
            }
            case 0x84: {
                reg_H = reg_H & resetBit0;
                break;
            }
            case 0x85: {
                reg_L = reg_L & resetBit0;
                break;
            }
            case 0x86: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit0);
                break;
            }
            case 0x87: {
                reg_A = reg_A & resetBit0;
                break;
            }
            case 0x88: {
                reg_B = reg_B & resetBit1;
                break;
            }
            case 0x89: {
                reg_C = reg_C & resetBit1;
                break;
            }
            case 0x8A: {
                reg_D = reg_D & resetBit1;
                break;
            }
            case 0x8B: {
                reg_E = reg_E & resetBit1;
                break;
            }
            case 0x8C: {
                reg_H = reg_H & resetBit1;
                break;
            }
            case 0x8D: {
                reg_L = reg_L & resetBit1;
                break;
            }
            case 0x8E: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit1);
                break;
            }
            case 0x8F: {
                reg_A = reg_A & resetBit1;
                break;
            }
            //
            case 0x90: {
                reg_B = reg_B & resetBit2;
                break;
            }
            case 0x91: {
                reg_C = reg_C & resetBit2;
                break;
            }
            case 0x92: {
                reg_D = reg_D & resetBit2;
                break;
            }
            case 0x93: {
                reg_E = reg_E & resetBit2;
                break;
            }
            case 0x94: {
                reg_H = reg_H & resetBit2;
                break;
            }
            case 0x95: {
                reg_L = reg_L & resetBit2;
                break;
            }
            case 0x96: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit2);
                break;
            }
            case 0x97: {
                reg_A = reg_A & resetBit2;
                break;
            }
            case 0x98: {
                reg_B = reg_B & resetBit3;
                break;
            }
            case 0x99: {
                reg_C = reg_C & resetBit3;
                break;
            }
            case 0x9A: {
                reg_D = reg_D & resetBit3;
                break;
            }
            case 0x9B: {
                reg_E = reg_E & resetBit3;
                break;
            }
            case 0x9C: {
                reg_H = reg_H & resetBit3;
                break;
            }
            case 0x9D: {
                reg_L = reg_L & resetBit3;
                break;
            }
            case 0x9E: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit3);
                break;
            }
            case 0x9F: {
                reg_A = reg_A & resetBit3;
                break;
            }
            //
            case 0xA0: {
                reg_B = reg_B & resetBit4;
                break;
            }
            case 0xA1: {
                reg_C = reg_C & resetBit4;
                break;
            }
            case 0xA2: {
                reg_D = reg_D & resetBit4;
                break;
            }
            case 0xA3: {
                reg_E = reg_E & resetBit4;
                break;
            }
            case 0xA4: {
                reg_H = reg_H & resetBit4;
                break;
            }
            case 0xA5: {
                reg_L = reg_L & resetBit4;
                break;
            }
            case 0xA6: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit4);
                break;
            }
            case 0xA7: {
                reg_A = reg_A & resetBit4;
                break;
            }
            case 0xA8: {
                reg_B = reg_B & resetBit5;
                break;
            }
            case 0xA9: {
                reg_C = reg_C & resetBit5;
                break;
            }
            case 0xAA: {
                reg_D = reg_D & resetBit5;
                break;
            }
            case 0xAB: {
                reg_E = reg_E & resetBit5;
                break;
            }
            case 0xAC: {
                reg_H = reg_H & resetBit5;
                break;
            }
            case 0xAD: {
                reg_L = reg_L & resetBit5;
                break;
            }
            case 0xAE: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit5);
                break;
            }
            case 0xAF: {
                reg_A = reg_A & resetBit5;
                break;
            }
            //
            case 0xB0: {
                reg_B = reg_B & resetBit6;
                break;
            }
            case 0xB1: {
                reg_C = reg_C & resetBit6;
                break;
            }
            case 0xB2: {
                reg_D = reg_D & resetBit6;
                break;
            }
            case 0xB3: {
                reg_E = reg_E & resetBit6;
                break;
            }
            case 0xB4: {
                reg_H = reg_H & resetBit6;
                break;
            }
            case 0xB5: {
                reg_L = reg_L & resetBit6;
                break;
            }
            case 0xB6: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit6);
                break;
            }
            case 0xB7: {
                reg_A = reg_A & resetBit6;
                break;
            }
            case 0xB8: {
                reg_B = reg_B & resetBit7;
                break;
            }
            case 0xB9: {
                reg_C = reg_C & resetBit7;
                break;
            }
            case 0xBA: {
                reg_D = reg_D & resetBit7;
                break;
            }
            case 0xBB: {
                reg_E = reg_E & resetBit7;
                break;
            }
            case 0xBC: {
                reg_H = reg_H & resetBit7;
                break;
            }
            case 0xBD: {
                reg_L = reg_L & resetBit7;
                break;
            }
            case 0xBE: {
                ram.writeByte(getHL(), ram.readByte(getHL()) & resetBit7);
                break;
            }
            case 0xBF: {
                reg_A = reg_A & resetBit7;
                break;
            }
            //
            case 0xC0: {
                reg_B = reg_B | setBit0;
                break;
            }
            case 0xC1: {
                reg_C = reg_C | setBit0;
                break;
            }
            case 0xC2: {
                reg_D = reg_D | setBit0;
                break;
            }
            case 0xC3: {
                reg_E = reg_E | setBit0;
                break;
            }
            case 0xC4: {
                reg_H = reg_H | setBit0;
                break;
            }
            case 0xC5: {
                reg_L = reg_L | setBit0;
                break;
            }
            case 0xC6: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit0);
                break;
            }
            case 0xC7: {
                reg_A = reg_A | setBit0;
                break;
            }
            case 0xC8: {
                reg_B = reg_B | setBit1;
                break;
            }
            case 0xC9: {
                reg_C = reg_C | setBit1;
                break;
            }
            case 0xCA: {
                reg_D = reg_D | setBit1;
                break;
            }
            case 0xCB: {
                reg_E = reg_E | setBit1;
                break;
            }
            case 0xCC: {
                reg_H = reg_H | setBit1;
                break;
            }
            case 0xCD: {
                reg_L = reg_L | setBit1;
                break;
            }
            case 0xCE: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit1);
                break;
            }
            case 0xCF: {
                reg_A = reg_A | setBit1;
                break;
            }
            //
            case 0xD0: {
                reg_B = reg_B | setBit2;
                break;
            }
            case 0xD1: {
                reg_C = reg_C | setBit2;
                break;
            }
            case 0xD2: {
                reg_D = reg_D | setBit2;
                break;
            }
            case 0xD3: {
                reg_E = reg_E | setBit2;
                break;
            }
            case 0xD4: {
                reg_H = reg_H | setBit2;
                break;
            }
            case 0xD5: {
                reg_L = reg_L | setBit2;
                break;
            }
            case 0xD6: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit2);
                break;
            }
            case 0xD7: {
                reg_A = reg_A | setBit2;
                break;
            }
            case 0xD8: {
                reg_B = reg_B | setBit3;
                break;
            }
            case 0xD9: {
                reg_C = reg_C | setBit3;
                break;
            }
            case 0xDA: {
                reg_D = reg_D | setBit3;
                break;
            }
            case 0xDB: {
                reg_E = reg_E | setBit3;
                break;
            }
            case 0xDC: {
                reg_H = reg_H | setBit3;
                break;
            }
            case 0xDD: {
                reg_L = reg_L | setBit3;
                break;
            }
            case 0xDE: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit3);
                break;
            }
            case 0xDF: {
                reg_A = reg_A | setBit3;
                break;
            }
            //
            case 0xE0: {
                reg_B = reg_B | setBit4;
                break;
            }
            case 0xE1: {
                reg_C = reg_C | setBit4;
                break;
            }
            case 0xE2: {
                reg_D = reg_D | setBit4;
                break;
            }
            case 0xE3: {
                reg_E = reg_E | setBit4;
                break;
            }
            case 0xE4: {
                reg_H = reg_H | setBit4;
                break;
            }
            case 0xE5: {
                reg_L = reg_L | setBit4;
                break;
            }
            case 0xE6: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit4);
                break;
            }
            case 0xE7: {
                reg_A = reg_A | setBit4;
                break;
            }
            case 0xE8: {
                reg_B = reg_B | setBit5;
                break;
            }
            case 0xE9: {
                reg_C = reg_C | setBit5;
                break;
            }
            case 0xEA: {
                reg_D = reg_D | setBit5;
                break;
            }
            case 0xEB: {
                reg_E = reg_E | setBit5;
                break;
            }
            case 0xEC: {
                reg_H = reg_H | setBit5;
                break;
            }
            case 0xED: {
                reg_L = reg_L | setBit5;
                break;
            }
            case 0xEE: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit5);
                break;
            }
            case 0xEF: {
                reg_A = reg_A | setBit5;
                break;
            }
            //
            case 0xF0: {
                reg_B = reg_B | setBit6;
                break;
            }
            case 0xF1: {
                reg_C = reg_C | setBit6;
                break;
            }
            case 0xF2: {
                reg_D = reg_D | setBit6;
                break;
            }
            case 0xF3: {
                reg_E = reg_E | setBit6;
                break;
            }
            case 0xF4: {
                reg_H = reg_H | setBit6;
                break;
            }
            case 0xF5: {
                reg_L = reg_L | setBit6;
                break;
            }
            case 0xF6: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit6);
                break;
            }
            case 0xF7: {
                reg_A = reg_A | setBit6;
                break;
            }
            case 0xF8: {
                reg_B = reg_B | setBit7;
                break;
            }
            case 0xF9: {
                reg_C = reg_C | setBit7;
                break;
            }
            case 0xFA: {
                reg_D = reg_D | setBit7;
                break;
            }
            case 0xFB: {
                reg_E = reg_E | setBit7;
                break;
            }
            case 0xFC: {
                reg_H = reg_H | setBit7;
                break;
            }
            case 0xFD: {
                reg_L = reg_L | setBit7;
                break;
            }
            case 0xFE: {
                ram.writeByte(getHL(), ram.readByte(getHL()) | setBit7);
                break;
            }
            default: {
                reg_A = reg_A | setBit7;
                break;
            }
        }
    }

    /*
     * *****************************************************************************
     *
     * Extended Instruction area
     *
     * *****************************************************************************
     */

    private void extendedED() throws ProcessorException {
        instruction = ram.readByte(reg_PC);
        incPC();
        tStates = tStates + config.OPCODE_ED_STATES[instruction];
        if ((instruction < 0x40) || (instruction >= 0xC0)) {
            throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
        }
        switch (instruction) {
            case 0x40: {
                inC(regCodeB);
                break;
            }
            case 0x41: {
                outC(regCodeB);
                break;
            }
            case 0x42: {
                ALU16BitSBC(regCodeBC);
                break;
            }
            case 0x43: {
                LDnnnnRegInd16Bit(regCodeBC);
                break;
            }
            case 0x44: {
                NEG();
                break;
            }
            case 0x45: {
                retn();
                break;
            }
            case 0x46: {
                IM(0);
                break;
            }
            case 0x47: {
                LDIA();
                break;
            }
            case 0x48: {
                inC(regCodeC);
                break;
            }
            case 0x49: {
                outC(regCodeC);
                break;
            }
            case 0x4A: {
                ALU16BitADC(regCodeBC);
                break;
            }
            case 0x4B: {
                LDRegnnnnInd16Bit(regCodeBC);
                break;
            }
            case 0x4C: {
                NEG();
                break;
            }
            case 0x4D: {
                reti();
                break;
            }
            case 0x4E: {
                IM(0);
                break;
            }
            case 0x4F: {
                LDRA();
                break;
            }
            //
            case 0x50: {
                inC(regCodeD);
                break;
            }
            case 0x51: {
                outC(regCodeD);
                break;
            }
            case 0x52: {
                ALU16BitSBC(regCodeDE);
                break;
            }
            case 0x53: {
                LDnnnnRegInd16Bit(regCodeDE);
                break;
            }
            case 0x54: {
                NEG();
                break;
            }
            case 0x55: {
                retn();
                break;
            }
            case 0x56: {
                IM(1);
                break;
            }
            case 0x57: {
                LDAI();
                break;
            }
            case 0x58: {
                inC(regCodeE);
                break;
            }
            case 0x59: {
                outC(regCodeE);
                break;
            }
            case 0x5A: {
                ALU16BitADC(regCodeDE);
                break;
            }
            case 0x5B: {
                LDRegnnnnInd16Bit(regCodeDE);
                break;
            }
            case 0x5C: {
                NEG();
                break;
            }
            case 0x5D: {
                retn();
                break;
            }
            case 0x5E: {
                IM(2);
                break;
            }
            case 0x5F: {
                LDAR();
                break;
            }
            //
            case 0x60: {
                inC(regCodeH);
                break;
            }
            case 0x61: {
                outC(regCodeH);
                break;
            }
            case 0x62: {
                ALU16BitSBC(regCodeHL);
                break;
            }
            case 0x63: {
                LDnnnnRegInd16Bit(regCodeHL);
                break;
            }
            case 0x64: {
                NEG();
                break;
            }
            case 0x65: {
                retn();
                break;
            }
            case 0x66: {
                IM(1);
                break;
            }
            case 0x67: {
                RRD();
                break;
            }
            case 0x68: {
                inC(regCodeL);
                break;
            }
            case 0x69: {
                outC(regCodeL);
                break;
            }
            case 0x6A: {
                ALU16BitADC(regCodeHL);
                break;
            }
            case 0x6B: {
                LDRegnnnnInd16Bit(regCodeHL);
                break;
            }
            case 0x6C: {
                NEG();
                break;
            }
            case 0x6D: {
                retn();
                break;
            }
            case 0x6E: {
                IM(1);
                break;
            }
            case 0x6F: {
                RLD();
                break;
            }
            //
            case 0x70: {
                inC(regCodeF);
                break;
            }
            case 0x71: {
                outC(regCodeF);
                break;
            }
            case 0x72: {
                ALU16BitSBC(regCodeSP);
                break;
            }
            case 0x73: {
                LDnnnnRegInd16Bit(regCodeSP);
                break;
            }
            case 0x74: {
                NEG();
                break;
            }
            case 0x75: {
                retn();
                break;
            }
            case 0x76: {
                IM(1);
                break;
            }
            case 0x77: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x78: {
                inC(regCodeA);
                break;
            }
            case 0x79: {
                outC(regCodeA);
                break;
            }
            case 0x7A: {
                ALU16BitADC(regCodeSP);
                break;
            }
            case 0x7B: {
                LDRegnnnnInd16Bit(regCodeSP);
                break;
            }
            case 0x7C: {
                NEG();
                break;
            }
            case 0x7D: {
                retn();
                break;
            }
            case 0x7E: {
                IM(2);
                break;
            }
            case 0x7F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x80: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x81: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x82: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x83: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x84: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x85: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x86: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x87: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x88: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x89: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8C: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8D: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8E: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x90: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x91: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x92: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x93: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x94: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x95: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x96: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x97: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x98: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x99: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9C: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9D: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9E: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xA0: {
                LDI();
                break;
            }
            case 0xA1: {
                CPI();
                break;
            }
            case 0xA2: {
                INI();
                break;
            }
            case 0xA3: {
                OUTI();
                break;
            }
            case 0xA4: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA5: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA6: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA8: {
                LDD();
                break;
            }
            case 0xA9: {
                CPD();
                break;
            }
            case 0xAA: {
                IND();
                break;
            }
            case 0xAB: {
                OUTD();
                break;
            }
            case 0xAC: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xAD: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xAE: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xAF: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xB0: {
                LDIR();
                break;
            }
            case 0xB1: {
                CPIR();
                break;
            }
            case 0xB2: {
                INIR();
                break;
            }
            case 0xB3: {
                OTIR();
                break;
            }
            case 0xB4: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB5: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB6: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB8: {
                LDDR();
                break;
            }
            case 0xB9: {
                CPDR();
                break;
            }
            case 0xBA: {
                INDR();
                break;
            }
            case 0xBB: {
                OTDR();
                break;
            }
            case 0xBC: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xBD: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xBE: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            default: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
        }
    }

    /*
     * *****************************************************************************
     *
     * IX and IY index register processing
     *
     * *****************************************************************************
     */

    /* IX register processing */
    private void extendedDD() throws ProcessorException {
        reg_index = reg_IX;
        extendedDDFD();
        reg_IX = reg_index;
    }

    /* IY register processing */
    private void extendedFD() throws ProcessorException {
        reg_index = reg_IY;
        extendedDDFD();
        reg_IY = reg_index;
    }

    /* generic index register processing */

    private void extendedDDFD() throws ProcessorException {
        instruction = ram.readByte(reg_PC);
        incPC();
        tStates = tStates + config.OPCODE_DD_FD_STATES[instruction];

        // primary decode stage
        switch (instruction) {
            case 0x00: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x01: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x02: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x03: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x04: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x05: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x06: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x07: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x08: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x09: {
                reg_index = ALU16BitAddIndexed(getBC());
                break;
            }
            case 0x0A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x0B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x0C: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x0D: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x0E: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x0F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x10: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x11: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x12: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x13: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x14: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x15: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x16: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x17: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x18: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x19: {
                reg_index = ALU16BitAddIndexed(getDE());
                break;
            }
            case 0x1A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x1B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x1C: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x1D: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x1E: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x1F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x20: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x21: {
                reg_index = ram.readWord(reg_PC);
                inc2PC();
                break;
            }
            case 0x22: {
                ram.writeWord(ram.readWord(reg_PC), reg_index);
                inc2PC();
                break;
            }
            case 0x23: {
                reg_index = ALU16BitInc(reg_index);
                break;
            }
            case 0x24: {
                int temp = reg_index >>> 8;
                temp = ALU8BitInc(temp);
                reg_index = (reg_index & 0x00FF) | (temp << 8);
                break;
            } // inc IXh
            case 0x25: {
                int temp = reg_index >>> 8;
                temp = ALU8BitDec(temp);
                reg_index = (reg_index & 0x00FF) | (temp << 8);
                break;
            } // dec IXh
            case 0x26: {
                int temp = ram.readByte(reg_PC) << 8;
                reg_index = (reg_index & 0x00FF) | temp;
                incPC();
                break;
            } // ld IXh, nn
            case 0x27: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x28: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x29: {
                reg_index = ALU16BitAddIndexed(reg_index);
                break;
            }
            case 0x2A: {
                reg_index = ram.readWord(ram.readWord(reg_PC));
                inc2PC();
                break;
            }
            case 0x2B: {
                reg_index = ALU16BitDec(reg_index);
                break;
            }
            case 0x2C: {
                int temp = reg_index & 0x00FF;
                temp = ALU8BitInc(temp);
                reg_index = (reg_index & 0xFF00) | temp;
                break;
            } // inc IXl
            case 0x2D: {
                int temp = reg_index & 0x00FF;
                temp = ALU8BitDec(temp);
                reg_index = (reg_index & 0xFF00) | temp;
                break;
            } // dec IXl
            case 0x2E: {
                int temp = ram.readByte(reg_PC);
                reg_index = (reg_index & 0xFF00) | temp;
                incPC();
                break;
            } // ld IXl, nn
            case 0x2F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x30: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x31: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x32: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x33: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x34: {
                incIndex();
                break;
            }
            case 0x35: {
                decIndex();
                break;
            }
            case 0x36: {
                loadIndex8BitImmediate();
                break;
            }
            case 0x37: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x38: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x39: {
                reg_index = ALU16BitAddIndexed(reg_SP);
                break;
            }
            case 0x3A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x3B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x3C: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x3D: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x3E: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x3F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x40: { /* reg_B = reg_B; */
                break;
            } // ld b, b
            case 0x41: {
                reg_B = reg_C;
                break;
            } // ld b, c
            case 0x42: {
                reg_B = reg_D;
                break;
            } // ld b, d
            case 0x43: {
                reg_B = reg_E;
                break;
            } // ld b, e
            case 0x44: {
                reg_B = getIndexAddressUndocumented(regCodeIXH);
                break;
            } // ld b, IXh
            case 0x45: {
                reg_B = getIndexAddressUndocumented(regCodeIXL);
                break;
            } // ld b, IXl
            case 0x46: {
                reg_B = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld b, (ix+dd)
            case 0x47: {
                reg_B = reg_A;
                break;
            } // ld b, a
            case 0x48: {
                reg_C = reg_B;
                break;
            } // ld c, b
            case 0x49: { /* reg_C = reg_C; */
                break;
            } // ld c, c
            case 0x4A: {
                reg_C = reg_D;
                break;
            } // ld c, d
            case 0x4B: {
                reg_C = reg_E;
                break;
            } // ld c, e
            case 0x4C: {
                reg_C = getIndexAddressUndocumented(regCodeIXH);
                break;
            } // ld c, IXh
            case 0x4D: {
                reg_C = getIndexAddressUndocumented(regCodeIXL);
                break;
            } // ld c, IXl
            case 0x4E: {
                reg_C = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld c, (ix+dd)
            case 0x4F: {
                reg_C = reg_A;
                break;
            } // ld c a
            //
            case 0x50: {
                reg_D = reg_B;
                break;
            } // ld d, b
            case 0x51: {
                reg_D = reg_C;
                break;
            } // ld d, c
            case 0x52: { /* reg_D = reg_D; */
                break;
            } // ld d, d
            case 0x53: {
                reg_D = reg_E;
                break;
            } // ld d, e
            case 0x54: {
                reg_D = getIndexAddressUndocumented(regCodeIXH);
                break;
            } // ld d, IXh
            case 0x55: {
                reg_D = getIndexAddressUndocumented(regCodeIXL);
                break;
            } // ld d, IXl
            case 0x56: {
                reg_D = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld d, (ix+dd)
            case 0x57: {
                reg_D = reg_A;
                break;
            } // ld d, a
            case 0x58: {
                reg_E = reg_B;
                break;
            } // ld e, b
            case 0x59: {
                reg_E = reg_C;
                break;
            } // ld e, c
            case 0x5A: {
                reg_E = reg_D;
                break;
            } // ld e, d
            case 0x5B: { /* reg_E = reg_E; */
                break;
            } // ld e, e
            case 0x5C: {
                reg_E = getIndexAddressUndocumented(regCodeIXH);
                break;
            } // ld e, IXh
            case 0x5D: {
                reg_E = getIndexAddressUndocumented(regCodeIXL);
                break;
            } // ld e, IXl
            case 0x5E: {
                reg_E = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld e, (ix+dd)
            case 0x5F: {
                reg_E = reg_A;
                break;
            } // ld e a
            //
            case 0x60: {
                setIndexAddressUndocumented(reg_B, regCodeIXH);
                break;
            } // ld ixh, b
            case 0x61: {
                setIndexAddressUndocumented(reg_C, regCodeIXH);
                break;
            } // ld ixh, c
            case 0x62: {
                setIndexAddressUndocumented(reg_D, regCodeIXH);
                break;
            } // ld ixh, d
            case 0x63: {
                setIndexAddressUndocumented(reg_E, regCodeIXH);
                break;
            } // ld ixh, e
            case 0x64: {
                setIndexAddressUndocumented(getIndexAddressUndocumented(regCodeIXH), regCodeIXH);
                break;
            } // ld ixh, IXh
            case 0x65: {
                setIndexAddressUndocumented(getIndexAddressUndocumented(regCodeIXL), regCodeIXH);
                break;
            } // ld ixh, IXl
            case 0x66: {
                reg_H = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld h, (ix+dd)
            case 0x67: {
                setIndexAddressUndocumented(reg_A, regCodeIXH);
                break;
            } // ld ixh, a
            case 0x68: {
                setIndexAddressUndocumented(reg_B, regCodeIXL);
                break;
            } // ld ixl, b
            case 0x69: {
                setIndexAddressUndocumented(reg_C, regCodeIXL);
                break;
            } // ld ixl, c
            case 0x6A: {
                setIndexAddressUndocumented(reg_D, regCodeIXL);
                break;
            } // ld ixl, d
            case 0x6B: {
                setIndexAddressUndocumented(reg_E, regCodeIXL);
                break;
            } // ld ixl, e
            case 0x6C: {
                setIndexAddressUndocumented(getIndexAddressUndocumented(regCodeIXH), regCodeIXL);
                break;
            } // ld ixl, IXh
            case 0x6D: {
                setIndexAddressUndocumented(getIndexAddressUndocumented(regCodeIXL), regCodeIXL);
                break;
            } // ld ixl, IXl
            case 0x6E: {
                reg_L = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld l, (ix+dd)
            case 0x6F: {
                setIndexAddressUndocumented(reg_A, regCodeIXL);
                break;
            } // ld ixl, a
            //
            case 0x70: {
                setIndexAddressUndocumented(reg_B, regCodeM);
                break;
            } // ld (ix+d), b
            case 0x71: {
                setIndexAddressUndocumented(reg_C, regCodeM);
                break;
            } // ld (ix+d), c
            case 0x72: {
                setIndexAddressUndocumented(reg_D, regCodeM);
                break;
            } // ld (ix+d), d
            case 0x73: {
                setIndexAddressUndocumented(reg_E, regCodeM);
                break;
            } // ld (ix+d), e
            case 0x74: {
                setIndexAddressUndocumented(get8BitRegisterIndexed(regCodeH), regCodeM);
                break;
            } // ld (ix+d), IXh
            case 0x75: {
                setIndexAddressUndocumented(get8BitRegisterIndexed(regCodeL), regCodeM);
                break;
            } // ld (ix+d), IXl
            case 0x76: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            } // ld (IX),(IX)
            case 0x77: {
                setIndexAddressUndocumented(get8BitRegisterIndexed(regCodeA), regCodeM);
                break;
            } // ld (ix+d), a
            case 0x78: {
                reg_A = reg_B;
                break;
            } // ld a, b
            case 0x79: {
                reg_A = reg_C;
                break;
            } // ld a, c
            case 0x7A: {
                reg_A = reg_D;
                break;
            } // ld a, d
            case 0x7B: {
                reg_A = reg_E;
                break;
            } // ld a, e
            case 0x7C: {
                reg_A = getIndexAddressUndocumented(regCodeIXH);
                break;
            } // ld a, IXh
            case 0x7D: {
                reg_A = getIndexAddressUndocumented(regCodeIXL);
                break;
            } // ld a, IXl
            case 0x7E: {
                reg_A = get8BitRegisterIndexed(regCodeM);
                break;
            } // ld a, (ix+dd)
            case 0x7F: { /* reg_A = reg_A; */
                break;
            } // ld a,a
            //
            case 0x80: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x81: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x82: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x83: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x84: {
                ALU8BitAdd((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0x85: {
                ALU8BitAdd(reg_index & 0x00FF);
                break;
            } // IXy
            case 0x86: {
                ALU8BitAdd(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0x87: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x88: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x89: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x8C: {
                ALU8BitAdc((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0x8D: {
                ALU8BitAdc(reg_index & 0x00FF);
                break;
            } // IXy
            case 0x8E: {
                ALU8BitAdc(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0x8F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0x90: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x91: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x92: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x93: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x94: {
                ALU8BitSub((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0x95: {
                ALU8BitSub(reg_index & 0x00FF);
                break;
            } // IXy
            case 0x96: {
                ALU8BitSub(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0x97: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x98: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x99: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9A: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9B: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0x9C: {
                ALU8BitSbc((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0x9D: {
                ALU8BitSbc(reg_index & 0x00FF);
                break;
            } // IXy
            case 0x9E: {
                ALU8BitSbc(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0x9F: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xA0: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA1: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA2: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA3: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA4: {
                ALU8BitAnd((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0xA5: {
                ALU8BitAnd(reg_index & 0x00FF);
                break;
            } // IXy
            case 0xA6: {
                ALU8BitAnd(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0xA7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA8: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xA9: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xAA: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xAB: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xAC: {
                ALU8BitXor((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0xAD: {
                ALU8BitXor(reg_index & 0x00FF);
                break;
            } // IXy
            case 0xAE: {
                ALU8BitXor(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0xAF: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xB0: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB1: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB2: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB3: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB4: {
                ALU8BitOr((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0xB5: {
                ALU8BitOr(reg_index & 0x00FF);
                break;
            } // IXy
            case 0xB6: {
                ALU8BitOr(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0xB7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB8: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xB9: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xBA: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xBB: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xBC: {
                ALU8BitCp((reg_index & 0xFF00) >>> 8);
                break;
            } // IXh
            case 0xBD: {
                ALU8BitCp(reg_index & 0x00FF);
                break;
            } // IXy
            case 0xBE: {
                ALU8BitCp(getIndexAddressUndocumented(regCodeM));
                break;
            } // CP (IX+dd)
            case 0xBF: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xC0: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC1: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC2: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC3: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC4: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC5: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC6: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC8: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xC9: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xCA: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xCB: {
                extendedIndexCB();
                break;
            }
            case 0xCC: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xCD: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xCE: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xCF: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xD0: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD1: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD2: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD3: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD4: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD5: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD6: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD8: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xD9: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xDA: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xDB: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xDC: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xDD: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xDE: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xDF: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xE0: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xE1: {
                reg_index = ram.readWord(reg_SP);
                inc2SP();
                break;
            } // pop ix
            case 0xE2: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xE3: {
                EXSPIndex();
                break;
            } // ex (sp),ix
            case 0xE4: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xE5: {
                dec2SP();
                ram.writeWord(reg_SP, reg_index);
                break;
            } // push ix
            case 0xE6: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xE7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xE8: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xE9: {
                reg_PC = reg_index;
                break;
            } // jp (ix)
            case 0xEA: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xEB: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xEC: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xED: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xEE: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xEF: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            //
            case 0xF0: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF1: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF2: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF3: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF4: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF5: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF6: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF7: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF8: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xF9: {
                reg_SP = reg_index;
                break;
            } // ld sp,ix
            case 0xFA: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xFB: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xFC: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xFD: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            case 0xFE: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            }
            default: {
                throw new ProcessorException(ProcessorException.COMPUTER_UNIMPLEMENTED_OPCODE);
            } //
        }

    }

    /*
     * *****************************************************************************
     *
     * CB Bit twiddling and shifting instructions for Index
     *
     * *****************************************************************************
     */
    private void extendedIndexCB() {
        instruction = ram.readByte(reg_PC + 1); // fudge for DD CB dd ii
        tStates = tStates + config.OPCODE_INDEXED_CB_STATES[instruction];

        switch (instruction) {
            case 0x00: {
                shiftRLCIndexed();
                break;
            }
            case 0x01: {
                shiftRLCIndexed();
                break;
            }
            case 0x02: {
                shiftRLCIndexed();
                break;
            }
            case 0x03: {
                shiftRLCIndexed();
                break;
            }
            case 0x04: {
                shiftRLCIndexed();
                break;
            }
            case 0x05: {
                shiftRLCIndexed();
                break;
            }
            case 0x06: {
                shiftRLCIndexed();
                break;
            }
            case 0x07: {
                shiftRLCIndexed();
                break;
            }
            case 0x08: {
                shiftRRCIndexed();
                break;
            }
            case 0x09: {
                shiftRRCIndexed();
                break;
            }
            case 0x0A: {
                shiftRRCIndexed();
                break;
            }
            case 0x0B: {
                shiftRRCIndexed();
                break;
            }
            case 0x0C: {
                shiftRRCIndexed();
                break;
            }
            case 0x0D: {
                shiftRRCIndexed();
                break;
            }
            case 0x0E: {
                shiftRRCIndexed();
                break;
            }
            case 0x0F: {
                shiftRRCIndexed();
                break;
            }
            //
            case 0x10: {
                shiftRLIndexed();
                break;
            }
            case 0x11: {
                shiftRLIndexed();
                break;
            }
            case 0x12: {
                shiftRLIndexed();
                break;
            }
            case 0x13: {
                shiftRLIndexed();
                break;
            }
            case 0x14: {
                shiftRLIndexed();
                break;
            }
            case 0x15: {
                shiftRLIndexed();
                break;
            }
            case 0x16: {
                shiftRLIndexed();
                break;
            }
            case 0x17: {
                shiftRLIndexed();
                break;
            }
            case 0x18: {
                shiftRRIndexed();
                break;
            }
            case 0x19: {
                shiftRRIndexed();
                break;
            }
            case 0x1A: {
                shiftRRIndexed();
                break;
            }
            case 0x1B: {
                shiftRRIndexed();
                break;
            }
            case 0x1C: {
                shiftRRIndexed();
                break;
            }
            case 0x1D: {
                shiftRRIndexed();
                break;
            }
            case 0x1E: {
                shiftRRIndexed();
                break;
            }
            case 0x1F: {
                shiftRRIndexed();
                break;
            }
            //
            case 0x20: {
                shiftSLAIndexed();
                break;
            }
            case 0x21: {
                shiftSLAIndexed();
                break;
            }
            case 0x22: {
                shiftSLAIndexed();
                break;
            }
            case 0x23: {
                shiftSLAIndexed();
                break;
            }
            case 0x24: {
                shiftSLAIndexed();
                break;
            }
            case 0x25: {
                shiftSLAIndexed();
                break;
            }
            case 0x26: {
                shiftSLAIndexed();
                break;
            }
            case 0x27: {
                shiftSLAIndexed();
                break;
            }
            case 0x28: {
                shiftSRAIndexed();
                break;
            }
            case 0x29: {
                shiftSRAIndexed();
                break;
            }
            case 0x2A: {
                shiftSRAIndexed();
                break;
            }
            case 0x2B: {
                shiftSRAIndexed();
                break;
            }
            case 0x2C: {
                shiftSRAIndexed();
                break;
            }
            case 0x2D: {
                shiftSRAIndexed();
                break;
            }
            case 0x2E: {
                shiftSRAIndexed();
                break;
            }
            case 0x2F: {
                shiftSRAIndexed();
                break;
            }
            //
            case 0x30: {
                shiftSLLIndexed();
                break;
            }
            case 0x31: {
                shiftSLLIndexed();
                break;
            }
            case 0x32: {
                shiftSLLIndexed();
                break;
            }
            case 0x33: {
                shiftSLLIndexed();
                break;
            }
            case 0x34: {
                shiftSLLIndexed();
                break;
            }
            case 0x35: {
                shiftSLLIndexed();
                break;
            }
            case 0x36: {
                shiftSLLIndexed();
                break;
            }
            case 0x37: {
                shiftSLLIndexed();
                break;
            }
            case 0x38: {
                shiftSRLIndexed();
                break;
            }
            case 0x39: {
                shiftSRLIndexed();
                break;
            }
            case 0x3A: {
                shiftSRLIndexed();
                break;
            }
            case 0x3B: {
                shiftSRLIndexed();
                break;
            }
            case 0x3C: {
                shiftSRLIndexed();
                break;
            }
            case 0x3D: {
                shiftSRLIndexed();
                break;
            }
            case 0x3E: {
                shiftSRLIndexed();
                break;
            }
            case 0x3F: {
                shiftSRLIndexed();
                break;
            }
            //
            case 0x40: {
                testIndexBit(0);
                break;
            }
            case 0x41: {
                testIndexBit(0);
                break;
            }
            case 0x42: {
                testIndexBit(0);
                break;
            }
            case 0x43: {
                testIndexBit(0);
                break;
            }
            case 0x44: {
                testIndexBit(0);
                break;
            }
            case 0x45: {
                testIndexBit(0);
                break;
            }
            case 0x46: {
                testIndexBit(0);
                break;
            }
            case 0x47: {
                testIndexBit(0);
                break;
            }
            case 0x48: {
                testIndexBit(1);
                break;
            }
            case 0x49: {
                testIndexBit(1);
                break;
            }
            case 0x4A: {
                testIndexBit(1);
                break;
            }
            case 0x4B: {
                testIndexBit(1);
                break;
            }
            case 0x4C: {
                testIndexBit(1);
                break;
            }
            case 0x4D: {
                testIndexBit(1);
                break;
            }
            case 0x4E: {
                testIndexBit(1);
                break;
            }
            case 0x4F: {
                testIndexBit(1);
                break;
            }
            //
            case 0x50: {
                testIndexBit(2);
                break;
            }
            case 0x51: {
                testIndexBit(2);
                break;
            }
            case 0x52: {
                testIndexBit(2);
                break;
            }
            case 0x53: {
                testIndexBit(2);
                break;
            }
            case 0x54: {
                testIndexBit(2);
                break;
            }
            case 0x55: {
                testIndexBit(2);
                break;
            }
            case 0x56: {
                testIndexBit(2);
                break;
            }
            case 0x57: {
                testIndexBit(2);
                break;
            }
            case 0x58: {
                testIndexBit(3);
                break;
            }
            case 0x59: {
                testIndexBit(3);
                break;
            }
            case 0x5A: {
                testIndexBit(3);
                break;
            }
            case 0x5B: {
                testIndexBit(3);
                break;
            }
            case 0x5C: {
                testIndexBit(3);
                break;
            }
            case 0x5D: {
                testIndexBit(3);
                break;
            }
            case 0x5E: {
                testIndexBit(3);
                break;
            }
            case 0x5F: {
                testIndexBit(3);
                break;
            }
            //
            case 0x60: {
                testIndexBit(4);
                break;
            }
            case 0x61: {
                testIndexBit(4);
                break;
            }
            case 0x62: {
                testIndexBit(4);
                break;
            }
            case 0x63: {
                testIndexBit(4);
                break;
            }
            case 0x64: {
                testIndexBit(4);
                break;
            }
            case 0x65: {
                testIndexBit(4);
                break;
            }
            case 0x66: {
                testIndexBit(4);
                break;
            }
            case 0x67: {
                testIndexBit(4);
                break;
            }
            case 0x68: {
                testIndexBit(5);
                break;
            }
            case 0x69: {
                testIndexBit(5);
                break;
            }
            case 0x6A: {
                testIndexBit(5);
                break;
            }
            case 0x6B: {
                testIndexBit(5);
                break;
            }
            case 0x6C: {
                testIndexBit(5);
                break;
            }
            case 0x6D: {
                testIndexBit(5);
                break;
            }
            case 0x6E: {
                testIndexBit(5);
                break;
            }
            case 0x6F: {
                testIndexBit(5);
                break;
            }
            //
            case 0x70: {
                testIndexBit(6);
                break;
            }
            case 0x71: {
                testIndexBit(6);
                break;
            }
            case 0x72: {
                testIndexBit(6);
                break;
            }
            case 0x73: {
                testIndexBit(6);
                break;
            }
            case 0x74: {
                testIndexBit(6);
                break;
            }
            case 0x75: {
                testIndexBit(6);
                break;
            }
            case 0x76: {
                testIndexBit(6);
                break;
            }
            case 0x77: {
                testIndexBit(6);
                break;
            }
            case 0x78: {
                testIndexBit(7);
                break;
            }
            case 0x79: {
                testIndexBit(7);
                break;
            }
            case 0x7A: {
                testIndexBit(7);
                break;
            }
            case 0x7B: {
                testIndexBit(7);
                break;
            }
            case 0x7C: {
                testIndexBit(7);
                break;
            }
            case 0x7D: {
                testIndexBit(7);
                break;
            }
            case 0x7E: {
                testIndexBit(7);
                break;
            }
            case 0x7F: {
                testIndexBit(7);
                break;
            }
            //
            case 0x80: {
                bitIndexReset(0);
                break;
            }
            case 0x81: {
                bitIndexReset(0);
                break;
            }
            case 0x82: {
                bitIndexReset(0);
                break;
            }
            case 0x83: {
                bitIndexReset(0);
                break;
            }
            case 0x84: {
                bitIndexReset(0);
                break;
            }
            case 0x85: {
                bitIndexReset(0);
                break;
            }
            case 0x86: {
                bitIndexReset(0);
                break;
            }
            case 0x87: {
                bitIndexReset(0);
                break;
            }
            case 0x88: {
                bitIndexReset(1);
                break;
            }
            case 0x89: {
                bitIndexReset(1);
                break;
            }
            case 0x8A: {
                bitIndexReset(1);
                break;
            }
            case 0x8B: {
                bitIndexReset(1);
                break;
            }
            case 0x8C: {
                bitIndexReset(1);
                break;
            }
            case 0x8D: {
                bitIndexReset(1);
                break;
            }
            case 0x8E: {
                bitIndexReset(1);
                break;
            }
            case 0x8F: {
                bitIndexReset(1);
                break;
            }
            //
            case 0x90: {
                bitIndexReset(2);
                break;
            }
            case 0x91: {
                bitIndexReset(2);
                break;
            }
            case 0x92: {
                bitIndexReset(2);
                break;
            }
            case 0x93: {
                bitIndexReset(2);
                break;
            }
            case 0x94: {
                bitIndexReset(2);
                break;
            }
            case 0x95: {
                bitIndexReset(2);
                break;
            }
            case 0x96: {
                bitIndexReset(2);
                break;
            }
            case 0x97: {
                bitIndexReset(2);
                break;
            }
            case 0x98: {
                bitIndexReset(3);
                break;
            }
            case 0x99: {
                bitIndexReset(3);
                break;
            }
            case 0x9A: {
                bitIndexReset(3);
                break;
            }
            case 0x9B: {
                bitIndexReset(3);
                break;
            }
            case 0x9C: {
                bitIndexReset(3);
                break;
            }
            case 0x9D: {
                bitIndexReset(3);
                break;
            }
            case 0x9E: {
                bitIndexReset(3);
                break;
            }
            case 0x9F: {
                bitIndexReset(3);
                break;
            }
            //
            case 0xA0: {
                bitIndexReset(4);
                break;
            }
            case 0xA1: {
                bitIndexReset(4);
                break;
            }
            case 0xA2: {
                bitIndexReset(4);
                break;
            }
            case 0xA3: {
                bitIndexReset(4);
                break;
            }
            case 0xA4: {
                bitIndexReset(4);
                break;
            }
            case 0xA5: {
                bitIndexReset(4);
                break;
            }
            case 0xA6: {
                bitIndexReset(4);
                break;
            }
            case 0xA7: {
                bitIndexReset(4);
                break;
            }
            case 0xA8: {
                bitIndexReset(5);
                break;
            }
            case 0xA9: {
                bitIndexReset(5);
                break;
            }
            case 0xAA: {
                bitIndexReset(5);
                break;
            }
            case 0xAB: {
                bitIndexReset(5);
                break;
            }
            case 0xAC: {
                bitIndexReset(5);
                break;
            }
            case 0xAD: {
                bitIndexReset(5);
                break;
            }
            case 0xAE: {
                bitIndexReset(5);
                break;
            }
            case 0xAF: {
                bitIndexReset(5);
                break;
            }
            //
            case 0xB0: {
                bitIndexReset(6);
                break;
            }
            case 0xB1: {
                bitIndexReset(6);
                break;
            }
            case 0xB2: {
                bitIndexReset(6);
                break;
            }
            case 0xB3: {
                bitIndexReset(6);
                break;
            }
            case 0xB4: {
                bitIndexReset(6);
                break;
            }
            case 0xB5: {
                bitIndexReset(6);
                break;
            }
            case 0xB6: {
                bitIndexReset(6);
                break;
            }
            case 0xB7: {
                bitIndexReset(6);
                break;
            }
            case 0xB8: {
                bitIndexReset(7);
                break;
            }
            case 0xB9: {
                bitIndexReset(7);
                break;
            }
            case 0xBA: {
                bitIndexReset(7);
                break;
            }
            case 0xBB: {
                bitIndexReset(7);
                break;
            }
            case 0xBC: {
                bitIndexReset(7);
                break;
            }
            case 0xBD: {
                bitIndexReset(7);
                break;
            }
            case 0xBE: {
                bitIndexReset(7);
                break;
            }
            case 0xBF: {
                bitIndexReset(7);
                break;
            }
            //
            case 0xC0: {
                bitIndexSet(0);
                break;
            }
            case 0xC1: {
                bitIndexSet(0);
                break;
            }
            case 0xC2: {
                bitIndexSet(0);
                break;
            }
            case 0xC3: {
                bitIndexSet(0);
                break;
            }
            case 0xC4: {
                bitIndexSet(0);
                break;
            }
            case 0xC5: {
                bitIndexSet(0);
                break;
            }
            case 0xC6: {
                bitIndexSet(0);
                break;
            }
            case 0xC7: {
                bitIndexSet(0);
                break;
            }
            case 0xC8: {
                bitIndexSet(1);
                break;
            }
            case 0xC9: {
                bitIndexSet(1);
                break;
            }
            case 0xCA: {
                bitIndexSet(1);
                break;
            }
            case 0xCB: {
                bitIndexSet(1);
                break;
            }
            case 0xCC: {
                bitIndexSet(1);
                break;
            }
            case 0xCD: {
                bitIndexSet(1);
                break;
            }
            case 0xCE: {
                bitIndexSet(1);
                break;
            }
            case 0xCF: {
                bitIndexSet(1);
                break;
            }
            //
            case 0xD0: {
                bitIndexSet(2);
                break;
            }
            case 0xD1: {
                bitIndexSet(2);
                break;
            }
            case 0xD2: {
                bitIndexSet(2);
                break;
            }
            case 0xD3: {
                bitIndexSet(2);
                break;
            }
            case 0xD4: {
                bitIndexSet(2);
                break;
            }
            case 0xD5: {
                bitIndexSet(2);
                break;
            }
            case 0xD6: {
                bitIndexSet(2);
                break;
            }
            case 0xD7: {
                bitIndexSet(2);
                break;
            }
            case 0xD8: {
                bitIndexSet(3);
                break;
            }
            case 0xD9: {
                bitIndexSet(3);
                break;
            }
            case 0xDA: {
                bitIndexSet(3);
                break;
            }
            case 0xDB: {
                bitIndexSet(3);
                break;
            }
            case 0xDC: {
                bitIndexSet(3);
                break;
            }
            case 0xDD: {
                bitIndexSet(3);
                break;
            }
            case 0xDE: {
                bitIndexSet(3);
                break;
            }
            case 0xDF: {
                bitIndexSet(3);
                break;
            }
            //
            case 0xE0: {
                bitIndexSet(4);
                break;
            }
            case 0xE1: {
                bitIndexSet(4);
                break;
            }
            case 0xE2: {
                bitIndexSet(4);
                break;
            }
            case 0xE3: {
                bitIndexSet(4);
                break;
            }
            case 0xE4: {
                bitIndexSet(4);
                break;
            }
            case 0xE5: {
                bitIndexSet(4);
                break;
            }
            case 0xE6: {
                bitIndexSet(4);
                break;
            }
            case 0xE7: {
                bitIndexSet(4);
                break;
            }
            case 0xE8: {
                bitIndexSet(5);
                break;
            }
            case 0xE9: {
                bitIndexSet(5);
                break;
            }
            case 0xEA: {
                bitIndexSet(5);
                break;
            }
            case 0xEB: {
                bitIndexSet(5);
                break;
            }
            case 0xEC: {
                bitIndexSet(5);
                break;
            }
            case 0xED: {
                bitIndexSet(5);
                break;
            }
            case 0xEE: {
                bitIndexSet(5);
                break;
            }
            case 0xEF: {
                bitIndexSet(5);
                break;
            }
            //
            case 0xF0: {
                bitIndexSet(6);
                break;
            }
            case 0xF1: {
                bitIndexSet(6);
                break;
            }
            case 0xF2: {
                bitIndexSet(6);
                break;
            }
            case 0xF3: {
                bitIndexSet(6);
                break;
            }
            case 0xF4: {
                bitIndexSet(6);
                break;
            }
            case 0xF5: {
                bitIndexSet(6);
                break;
            }
            case 0xF6: {
                bitIndexSet(6);
                break;
            }
            case 0xF7: {
                bitIndexSet(6);
                break;
            }
            case 0xF8: {
                bitIndexSet(7);
                break;
            }
            case 0xF9: {
                bitIndexSet(7);
                break;
            }
            case 0xFA: {
                bitIndexSet(7);
                break;
            }
            case 0xFB: {
                bitIndexSet(7);
                break;
            }
            case 0xFC: {
                bitIndexSet(7);
                break;
            }
            case 0xFD: {
                bitIndexSet(7);
                break;
            }
            case 0xFE: {
                bitIndexSet(7);
                break;
            }
            default: {
                bitIndexSet(7);
                break;
            }
        }
        incPC();
    }

    /*
     * return an 8 bit register based on its code 000 -> 111
     */
    private int get8BitRegisterForIO(int reg) {
        switch (reg) {
            case 0: {
                return reg_B;
            } // B
            case 1: {
                return reg_C;
            } // C
            case 2: {
                return reg_D;
            } // D
            case 3: {
                return reg_E;
            } // E
            case 4: {
                return reg_H;
            } // H
            case 5: {
                return reg_L;
            } // L
            case 7: {
                return reg_A;
            } // F
            default: {
                return 0;
            }
        }
    }

    /*
     * return a 16 bit register based on its code 00 -> 11
     */
    private int get16BitRegister(int reg) {
        switch (reg) {
            case 0: {
                return getBC();
            }
            case 1: {
                return getDE();
            }
            case 2: {
                return getHL();
            }
            default: {
                return reg_SP;
            }
        }
    }

    /*
     * set a 16 bit register based on its code 00 -> 11
     */
    private void set16BitRegister(int value, int reg) {
        switch (reg) {
            case 0: {
                setBC(value);
                break;
            }
            case 1: {
                setDE(value);
                break;
            }
            case 2: {
                setHL(value);
                break;
            }
            default: {
                reg_SP = value;
                break;
            }
        }
    }

    /*
     * increment (and wrap) the program counter
     */
    private void incPC() {
        reg_PC++;
        reg_PC = reg_PC & MAX_ADDRESS;
    }

    private void decPC() {
        reg_PC--;
        reg_PC = reg_PC & MAX_ADDRESS;
    }

    private void inc2PC() {
        reg_PC = reg_PC + 2;
        reg_PC = reg_PC & MAX_ADDRESS;
    }

    private void dec2PC() {
        reg_PC = reg_PC - 2;
        reg_PC = reg_PC & MAX_ADDRESS;
    }

    /*
     * increment / decrement (and wrap) the stack pointer
     */
    private void inc2SP() {
        reg_SP = reg_SP + 2;
        reg_SP = reg_SP & MAX_ADDRESS;
    }

    private void dec2SP() {
        reg_SP = reg_SP - 2;
        reg_SP = reg_SP & MAX_ADDRESS;
    }

    /*
     * ALU Operations
     */

    /* half carry flag control */
    private void setHalfCarryFlagAdd(int left, int right, int carry) {
        left = left & 0x000f;
        right = right & 0x000f;
        setH((right + left + carry) > 0x0f);
    }

    /* half carry flag control */
    private void setHalfCarryFlagAdd(int left, int right) {
        left = left & 0x000F;
        right = right & 0x000F;
        setH((right + left) > 0x0F);
    }

    /* half carry flag control */
    private void setHalfCarryFlagSub(int left, int right) {
        left = left & 0x000F;
        right = right & 0x000F;
        setH(left < right);
    }

    /* half carry flag control */
    private void setHalfCarryFlagSub(int left, int right, int carry) {
        left = left & 0x000F;
        right = right & 0x000F;
        setH(left < (right + carry));
    }

    /* half carry flag control */
    /*
     * private void setHalfCarryFlagSub16(int left, int right, int carry) { left = left & 0x0FFF; right = right &
     * 0x0FFF; setH ( left < (right+carry) ); }
     */
    /* 2's compliment overflow flag control */
    private void setOverflowFlagAdd(int left, int right, int carry) {
        if (left > 127)
            left = left - 256;
        if (right > 127)
            right = right - 256;
        left = left + right + carry;
        setPV((left < -128) || (left > 127));
    }

    /* 2's compliment overflow flag control */
    private void setOverflowFlagAdd(int left, int right) {
        setOverflowFlagAdd(left, right, 0);
    }

    /* 2's compliment overflow flag control */
    private void setOverflowFlagAdd16(int left, int right, int carry) {
        if (left > 32767)
            left = left - 65536;
        if (right > 32767)
            right = right - 65536;
        left = left + right + carry;
        setPV((left < -32768) || (left > 32767));
    }

    /* 2's compliment overflow flag control */
    private void setOverflowFlagSub(int left, int right, int carry) {
        if (left > 127)
            left = left - 256;
        if (right > 127)
            right = right - 256;
        left = left - right - carry;
        setPV((left < -128) || (left > 127));
    }

    /* 2's compliment overflow flag control */
    private void setOverflowFlagSub(int left, int right) {
        setOverflowFlagSub(left, right, 0);
    }

    /* 2's compliment overflow flag control */
    private void setOverflowFlagSub16(int left, int right, int carry) {
        if (left > 32767)
            left = left - 65536;
        if (right > 32767)
            right = right - 65536;
        left = left - right - carry;
        setPV((left < -32768) || (left > 32767));
    }

    /* 8 bit ADD */
    private void ALU8BitAdd(int value) {
        int local_reg_A = reg_A;

        setHalfCarryFlagAdd(local_reg_A, value);
        setOverflowFlagAdd(local_reg_A, value);
        local_reg_A = local_reg_A + value;
        setS((local_reg_A & 0x0080) != 0);
        setC((local_reg_A & 0xff00) != 0);
        local_reg_A = local_reg_A & 0x00ff;
        setZ(local_reg_A == 0);
        resetN();
        reg_A = local_reg_A;
        setUnusedFlags(reg_A);
    }

    /* 8 bit ADC */
    private void ALU8BitAdc(int value) {
        int local_reg_A = reg_A;
        int carry;

        if (getC())
            carry = 1;
        else
            carry = 0;
        setHalfCarryFlagAdd(local_reg_A, value, carry);
        setOverflowFlagAdd(local_reg_A, value, carry);
        local_reg_A = local_reg_A + value + carry;
        setS((local_reg_A & 0x0080) != 0);
        setC((local_reg_A & 0xff00) != 0);
        local_reg_A = local_reg_A & 0x00ff;
        setZ(local_reg_A == 0);
        resetN();
        reg_A = local_reg_A;
        setUnusedFlags(reg_A);
    }

    /* 8 bit SUB */
    private void ALU8BitSub(int value) {
        int local_reg_A = reg_A;
        setHalfCarryFlagSub(local_reg_A, value);
        setOverflowFlagSub(local_reg_A, value);
        local_reg_A = local_reg_A - value;
        setS((local_reg_A & 0x0080) != 0);
        setC((local_reg_A & 0xff00) != 0);
        local_reg_A = local_reg_A & 0x00ff;
        setZ(local_reg_A == 0);
        setN();
        reg_A = local_reg_A;
        setUnusedFlags(reg_A);
    }

    /* 8 bit SBC */
    private void ALU8BitSbc(int value) {
        int local_reg_A = reg_A;
        int carry;

        if (getC())
            carry = 1;
        else
            carry = 0;
        setHalfCarryFlagSub(local_reg_A, value, carry);
        setOverflowFlagSub(local_reg_A, value, carry);
        local_reg_A = local_reg_A - value - carry;
        setS((local_reg_A & 0x0080) != 0);
        setC((local_reg_A & 0xff00) != 0);
        local_reg_A = local_reg_A & 0x00ff;
        setZ(local_reg_A == 0);
        setN();
        reg_A = local_reg_A;
        setUnusedFlags(reg_A);
    }

    /* 8 bit AND (version II) */
    private void ALU8BitAnd(int value) {
        reg_F = 0x10; // set the H flag
        reg_A = reg_A & value;
        setS((reg_A & 0x0080) != 0);
        setZ(reg_A == 0);
        setPV(PARITY_TABLE[reg_A]);
        setUnusedFlags(reg_A);
    }

    /* 8 bit OR (Version II) */
    private void ALU8BitOr(int value) {
        reg_F = 0;
        reg_A = reg_A | value;
        setS((reg_A & 0x0080) != 0);
        setZ(reg_A == 0);
        setPV(PARITY_TABLE[reg_A]);
        setUnusedFlags(reg_A);
    }

    /* 8 bit XOR (Version II) */
    private void ALU8BitXor(int value) {
        reg_F = 0;
        reg_A = reg_A ^ value;
        setS((reg_A & 0x0080) != 0);
        setZ(reg_A == 0);
        setPV(PARITY_TABLE[reg_A]);
        setUnusedFlags(reg_A);
    }

    /* 8 bit CP */
    private void ALU8BitCp(int b) {
        final int a = reg_A;
        final int wans = a - b;
        final int ans = wans & 0xff;
        reg_F = 0x02;
        setS((ans & flag_S) != 0);
        set3((b & flag_3) != 0);
        set5((b & flag_5) != 0);
        setZ(ans == 0);
        setC((wans & 0x100) != 0);
        setH((((a & 0x0f) - (b & 0x0f)) & flag_H) != 0);
        setPV(((a ^ b) & (a ^ ans) & 0x80) != 0);
    }

    /* 8 bit INC */
    private int ALU8BitInc(int value) {
        if (getC())
            reg_F = 0x01;
        else
            reg_F = 0x00;
        setHalfCarryFlagAdd(value, 1);
        // setOverflowFlagAdd(value, 1);
        setPV(value == 0x7F);
        value++;
        setS((value & 0x0080) != 0);
        value = value & 0x00ff;
        setZ(value == 0);
        resetN();
        setUnusedFlags(value);
        return (value);
    }

    /* 8 bit DEC */
    private int ALU8BitDec(int value) {
        if (getC())
            reg_F = 0x01;
        else
            reg_F = 0x00;
        setHalfCarryFlagSub(value, 1);
        // setOverflowFlagSub(value, 1);
        setPV(value == 0x80);
        value--;
        setS((value & 0x0080) != 0);
        value = value & 0x00ff;
        setZ(value == 0);
        setN();
        setUnusedFlags(value);
        return (value);
    }

    /* 16 bit INC */
    private int ALU16BitInc(int value) {
        value++;
        return (value & lsw);
    }

    /* 16 bit DEC */
    private int ALU16BitDec(int value) {
        value--;
        return (value & lsw);
    }

    /* 16 bit ADD */
    private int ALU16BitAdd(int value) {
        int result = getHL() + value; // ADD HL,rr
        resetN(); // N = 0;
        //
        int temp = (getHL() & 0x0FFF) + (value & 0x0FFF);
        if ((temp & 0xF000) != 0)
            setH();
        else
            resetH();
        // temp = result >> 8;
        if ((result & 0x0800) != 0)
            set3();
        else
            reset3();
        if ((result & 0x2000) != 0)
            set5();
        else
            reset5();
        //
        if (result > lsw) // overflow ?
        {
            setC();
            return (result & lsw);
        } else {
            resetC();
            return result;
        }
    }

    /* 16 bit ADD */
    private int ALU16BitAddIndexed(int value) {
        int result = reg_index + value; // ADD IX,rr
        resetN(); // N = 0;
        int temp = (reg_index & 0x0FFF) + (value & 0x0FFF);
        if ((temp & 0xF000) != 0)
            setH();
        else
            resetH();
        // temp = result >> 8;
        if ((result & 0x0800) != 0)
            set3();
        else
            reset3();
        if ((result & 0x2000) != 0)
            set5();
        else
            reset5();
        //
        if (result > lsw) // overflow ?
        {
            setC();
            return (result & lsw);
        } else {
            resetC();
            return result;
        }
    }

    /* 16 bit ADC */
    private void ALU16BitADC(int regCode) {
        int a = getHL();
        int b = get16BitRegister((byte) regCode);
        int c = getC() ? 1 : 0;
        int lans = a + b + c;
        int ans = lans & 0xffff;
        setS((ans & (flag_S << 8)) != 0);
        set3((ans & (0x08 << 8)) != 0);
        set5((ans & (0x20 << 8)) != 0);
        setZ(ans == 0);
        setC(lans > 0xFFFF);
        // setPV( ((a ^ b) & (a ^ ans) & 0x8000)!=0 );
        setOverflowFlagAdd16(a, b, c);
        if ((((a & 0x0fff) + (b & 0x0fff) + c) & 0x1000) != 0)
            setH();
        else
            resetH();
        resetN();
        setHL(ans);
    }

    /* 16 bit SBC */
    private void ALU16BitSBC(int regCode) {
        int a = getHL();
        int b = get16BitRegister((byte) regCode);
        int c = getC() ? 1 : 0;
        int lans = a - b - c;
        int ans = lans & 0xffff;
        setS((ans & (flag_S << 8)) != 0);
        set3((ans & (0x08 << 8)) != 0);
        set5((ans & (0x20 << 8)) != 0);
        setZ(ans == 0);
        setC(lans < 0);
        // setPV( ((a ^ b) & (a ^ ans) & 0x8000)!=0 );
        setOverflowFlagSub16(a, b, c);
        if ((((a & 0x0fff) - (b & 0x0fff) - c) & 0x1000) != 0)
            setH();
        else
            resetH();
        setN();
        setHL(ans);
    }

    /*
     * varous register swap operations
     */
    private void EXAFAF() {
        int temp;

        temp = reg_A;
        reg_A = reg_A_ALT;
        reg_A_ALT = temp;
        temp = reg_F;
        reg_F = reg_F_ALT;
        reg_F_ALT = temp;
    }

    private void EXDEHL() {
        int temp = getHL();
        setHL(getDE());
        setDE(temp);
    }

    private void EXSPHL() {
        int temp = getHL();
        setHL(ram.readWord(reg_SP));
        inc2SP();
        dec2SP();
        ram.writeWord(reg_SP, temp);
    }

    private void EXX() {
        int temp;
        temp = getBC();
        setBC(getBC_ALT());
        setBC_ALT(temp);
        temp = getDE();
        setDE(getDE_ALT());
        setDE_ALT(temp);
        temp = getHL();
        setHL(getHL_ALT());
        setHL_ALT(temp);
    }

    /*
     * test & set flag states
     */
    private boolean getS() {
        return ((reg_F & flag_S) != 0);
    }

    private void setS(boolean b) {
        if (b)
            setS();
        else
            resetS();
    }

    private boolean getZ() {
        return ((reg_F & flag_Z) != 0);
    }

    private void setZ(boolean b) {
        if (b)
            setZ();
        else
            resetZ();
    }

    private boolean getH() {
        return ((reg_F & flag_H) != 0);
    }

    private void setH(boolean b) {
        if (b)
            setH();
        else
            resetH();
    }

    private boolean getPV() {
        return ((reg_F & flag_PV) != 0);
    }

    private void setPV(boolean b) {
        if (b)
            setPV();
        else
            resetPV();
    }

    private boolean getN() {
        return ((reg_F & flag_N) != 0);
    }

    private boolean getC() {
        return ((reg_F & flag_C) != 0);
    }

    // private void setN(boolean b) { if (b) setN(); else resetN(); }
    private void setC(boolean b) {
        if (b)
            setC();
        else
            resetC();
    }

    private void setS() {
        reg_F = reg_F | flag_S;
    }

    private void setZ() {
        reg_F = reg_F | flag_Z;
    }

    private void set5() {
        reg_F = reg_F | flag_5;
    }

    private void setH() {
        reg_F = reg_F | flag_H;
    }

    private void set3() {
        reg_F = reg_F | flag_3;
    }

    private void setPV() {
        reg_F = reg_F | flag_PV;
    }

    private void setN() {
        reg_F = reg_F | flag_N;
    }

    private void setC() {
        reg_F = reg_F | flag_C;
    }

    private void set5(boolean b) {
        if (b)
            set5();
        else
            reset5();
    }

    private void set3(boolean b) {
        if (b)
            set3();
        else
            reset3();
    }

    private void setUnusedFlags(int value) {
        value = value & 0x28;
        reg_F = reg_F & 0xD7;
        reg_F = reg_F | value;
    }

    private void flipC() {
        reg_F = reg_F ^ flag_C;
    }

    private void resetS() {
        reg_F = reg_F & flag_S_N;
    }

    private void resetZ() {
        reg_F = reg_F & flag_Z_N;
    }

    private void reset5() {
        reg_F = reg_F & flag_5_N;
    }

    private void resetH() {
        reg_F = reg_F & flag_H_N;
    }

    private void reset3() {
        reg_F = reg_F & flag_3_N;
    }

    private void resetPV() {
        reg_F = reg_F & flag_PV_N;
    }

    private void resetN() {
        reg_F = reg_F & flag_N_N;
    }

    private void resetC() {
        reg_F = reg_F & flag_C_N;
    }

    private int getBC() {
        return (reg_B << 8) + reg_C;
    }

    private void setBC(int bc) {
        reg_B = (bc & 0xFF00) >> 8;
        reg_C = bc & 0x00FF;
    }

    private int getDE() {
        return (reg_D << 8) + reg_E;
    }

    private void setDE(int de) {
        reg_D = (de & 0xFF00) >> 8;
        reg_E = de & 0x00FF;
    }

    private int getHL() {
        return (reg_H << 8) + reg_L;
    }

    private void setHL(int hl) {
        reg_H = (hl & 0xFF00) >> 8;
        reg_L = hl & 0x00FF;
    }

    private int getBC_ALT() {
        return (reg_B_ALT << 8) + reg_C_ALT;
    }

    private void setBC_ALT(int bc) {
        reg_B_ALT = (bc & 0xFF00) >> 8;
        reg_C_ALT = bc & 0x00FF;
    }

    private int getDE_ALT() {
        return (reg_D_ALT << 8) + reg_E_ALT;
    }

    private void setDE_ALT(int de) {
        reg_D_ALT = (de & 0xFF00) >> 8;
        reg_E_ALT = de & 0x00FF;
    }

    private int getHL_ALT() {
        return (reg_H_ALT << 8) + reg_L_ALT;
    }

    private void setHL_ALT(int hl) {
        reg_H_ALT = (hl & 0xFF00) >> 8;
        reg_L_ALT = hl & 0x00FF;
    }

    /*
     * shifts and rotates
     */

    private void RLCA() {
        boolean carry = (reg_A & 0x0080) != 0;
        reg_A = ((reg_A << 1) & 0x00FF);
        if (carry) {
            setC();
            reg_A = (reg_A | 0x0001);
        } else
            resetC();
        resetH();
        resetN();
        setUnusedFlags(reg_A);
    }

    private void RLA() {
        boolean carry = (reg_A & 0x0080) != 0;

        reg_A = ((reg_A << 1) & 0x00FF);
        if (getC())
            reg_A = reg_A | 0x01;
        if (carry)
            setC();
        else
            resetC();
        resetH();
        resetN();
        setUnusedFlags(reg_A);
    }

    private void RRCA() {
        boolean carry = (reg_A & 0x0001) != 0;

        reg_A = (reg_A >> 1);
        if (carry) {
            setC();
            reg_A = (reg_A | 0x0080);
        } else
            resetC();
        resetH();
        resetN();
        setUnusedFlags(reg_A);
    }

    private void RRA() {
        boolean carry = (reg_A & 0x01) != 0;

        reg_A = (reg_A >> 1);
        if (getC())
            reg_A = (reg_A | 0x0080);
        if (carry)
            setC();
        else
            resetC();
        resetH();
        resetN();
        setUnusedFlags(reg_A);
    }

    private void CPL() {
        reg_A = reg_A ^ 0x00FF;
        setH();
        setN();
        setUnusedFlags(reg_A);
    }

    private void NEG() {
        setHalfCarryFlagSub(0, reg_A, 0);
        // if ((value & 0x0f) == 0x00) setH(); else resetH();
        setOverflowFlagSub(0, reg_A, 0);
        // if (value == 0x80) setPV(); else resetPV();
        reg_A = -reg_A;
        if ((reg_A & 0xFF00) != 0)
            setC();
        else
            resetC();
        setN();
        reg_A = reg_A & 0x00FF;
        if (reg_A == 0)
            setZ();
        else
            resetZ();
        if ((reg_A & 0x0080) != 0)
            setS();
        else
            resetS();
        setUnusedFlags(reg_A);
    }

    private void SCF() {
        setC();
        resetH();
        resetN();
        setUnusedFlags(reg_A);
    }

    private void CCF() {
        if (getC())
            setH();
        else
            resetH();
        flipC();
        resetN();
        setUnusedFlags(reg_A);
    }

    /*
     * DAA is weird, can't find Zilog algorithm so using +0110 if Nibble>9 algorithm.
     */
    private void DAA() {
        int ans = reg_A;
        int incr = 0;
        boolean carry = getC();
        if ((getH()) || ((ans & 0x0f) > 0x09)) {
            incr = 0x06;
        }
        if (carry || (ans > 0x9f) || ((ans > 0x8f) && ((ans & 0x0f) > 0x09))) {
            incr |= 0x60;
        }
        if (ans > 0x99) {
            carry = true;
        }
        if (getN()) {
            ALU8BitSub(incr); // sub_a(incr);
        } else {
            ALU8BitAdd(incr); // add_a(incr);
        }
        ans = reg_A;
        if (carry)
            setC();
        else
            resetC(); // setC( carry );
        setPV(PARITY_TABLE[ans]); // setPV( PARITY_TABLE[ ans ] );
    }

    private int shiftGenericRLC(int temp) {
        temp = temp << 1;
        if ((temp & 0x0FF00) != 0) {
            setC();
            temp = temp | 0x01;
        } else
            resetC();
        // standard flag updates
        if ((temp & flag_S) == 0)
            resetS();
        else
            setS();
        if ((temp & 0x00FF) == 0)
            setZ();
        else
            resetZ();
        resetH();
        resetN();
        // put value back
        temp = temp & 0x00FF;
        setPV(PARITY_TABLE[temp]);
        setUnusedFlags(temp);
        return temp;
    }

    /**
     * Extra weird RLC (IX+nn) & LD R,(IX+nn)
     */
    private void shiftRLCIndexed() {
        int address = getIndexAddress();
        int regValue = shiftGenericRLC(ram.readByte(address));
        ram.writeByte(address, regValue);
        reg_R++;
    }

    private int shiftGenericRL(int temp) {
        // do shift operation
        temp = temp << 1;
        if (getC())
            temp = temp | 0x01;
        // standard flag updates
        setS((temp & 0x0080) != 0);
        if ((temp & 0x0FF00) == 0)
            resetC();
        else
            setC();
        temp = temp & lsb;
        if ((temp & 0x00FF) == 0)
            setZ();
        else
            resetZ();
        setPV(PARITY_TABLE[temp]);
        resetH();
        resetN();
        // put value back
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftRLIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericRL(ram.readByte(address)));
        reg_R++;
    }

    private int shiftGenericRRC(int temp) {
        // do shift operation
        setC((temp & 0x0001) != 0);
        temp = temp >> 1;
        if (getC())
            temp = temp | 0x80;
        // standard flag updates
        setS((temp & 0x0080) != 0);
        if (temp == 0)
            setZ();
        else
            resetZ();
        resetH();
        setPV(PARITY_TABLE[temp]);
        resetN();
        // put value back
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftRRCIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericRRC(ram.readByte(address)));
        reg_R++;
    }

    private int shiftGenericRR(int temp) {
        boolean tempC;
        // do shift operation
        tempC = getC();
        setC((temp & 0x0001) != 0);
        temp = temp >> 1;
        if (tempC)
            temp = temp | 0x80;
        // standard flag updates
        setS((temp & 0x0080) != 0);
        if (temp == 0)
            setZ();
        else
            resetZ();
        resetH();
        setPV(PARITY_TABLE[temp]);
        resetN();
        // put value back
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftRRIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericRR(ram.readByte(address)));
        reg_R++;
    }

    private int shiftGenericSLA(int temp) {
        // do shift operation
        temp = temp << 1;
        // standard flag updates
        setS((temp & 0x0080) != 0);
        if ((temp & 0x00FF) == 0)
            setZ();
        else
            resetZ();
        resetH();
        if ((temp & 0x0FF00) != 0)
            setC();
        else
            resetC();
        temp = temp & 0x00FF;
        setPV(PARITY_TABLE[temp]);
        resetN();
        // put value back
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftSLAIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericSLA(ram.readByte(address)));
        reg_R++;
    }

    /**
     * Note: This implements the broken (and undocumented) SLL instructions. Faulty as it feeds in a zero into bit 0
     *
     * @param temp Register value
     * @return Incorrect SLL value
     */
    private int shiftGenericSLL(int temp) {
        // do shift operation
        temp = (temp << 1) | 0x01; // the fault
        // standard flag updates
        setS((temp & 0x0080) != 0);
        resetZ();
        resetH();
        if ((temp & 0x0FF00) != 0)
            setC();
        else
            resetC();
        temp = temp & 0x00FF;
        setPV(PARITY_TABLE[temp]);
        resetN();
        // put value back
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftSLLIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericSLL(ram.readByte(address)));
        reg_R++;
    }

    private int shiftGenericSRA(int temp) {
        // do shift operation
        setC((temp & 0x0001) != 0);
        if ((temp & 0x0080) == 0) {
            temp = temp >> 1;
            resetS();
        } else {
            temp = (temp >> 1) | 0x0080;
            setS();
        }
        // standard flag updates
        if (temp == 0)
            setZ();
        else
            resetZ();
        resetH();
        setPV(PARITY_TABLE[temp]);
        resetN();
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftSRAIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericSRA(ram.readByte(address)));
        reg_R++;
    }

    private int shiftGenericSRL(int temp) {
        // do shift operation
        setC((temp & 0x0001) != 0);
        temp = temp >> 1;
        // standard flag updates
        resetS();
        setZ(temp == 0);
        resetH();
        setPV(PARITY_TABLE[temp]);
        resetN();
        // put value back
        setUnusedFlags(temp);
        return temp;
    }

    private void shiftSRLIndexed() {
        int address = getIndexAddress();
        ram.writeByte(address, shiftGenericSRL(ram.readByte(address)));
        reg_R++;
    }

    private void RRD() {
        reg_R++;
        int temp = ram.readByte(getHL());
        int nibble1 = (reg_A & 0x00F0) >> 4;
        int nibble2 = reg_A & 0x000F;
        int nibble3 = (temp & 0x00F0) >> 4;
        int nibble4 = temp & 0x000F;
        //
        reg_A = (nibble1 << 4) | nibble4;
        temp = (nibble2 << 4) | nibble3;
        //
        ram.writeByte(getHL(), temp);
        // standard flag updates
        if ((reg_A & 0x80) == 0)
            resetS();
        else
            setS();
        setZ(reg_A == 0);
        resetH();
        setPV(PARITY_TABLE[reg_A]);
        resetN();
        setUnusedFlags(reg_A);
    }

    private void RLD() {
        reg_R++;
        int temp = ram.readByte(getHL());
        int nibble1 = (reg_A & 0x00F0) >> 4;
        int nibble2 = reg_A & 0x000F;
        int nibble3 = (temp & 0x00F0) >> 4;
        int nibble4 = temp & 0x000F;
        //
        reg_A = (nibble1 << 4) | nibble3;
        temp = (nibble4 << 4) | nibble2;
        //
        ram.writeByte(getHL(), temp);
        // standard flag updates
        if ((reg_A & 0x80) == 0)
            resetS();
        else
            setS();
        if (reg_A == 0)
            setZ();
        else
            resetZ();
        resetH();
        setPV(PARITY_TABLE[reg_A]);
        resetN();
        setUnusedFlags(reg_A);
    }

    /*
     * calls, jumps and returns + associated stack operations
     */
    private void relativeJump() {
        reg_R++;
        int offset = ram.readByte(reg_PC);
        if (offset > 0x007F)
            offset = offset - 0x0100;
        reg_PC++;
        reg_PC = (reg_PC + offset) & MAX_ADDRESS;
    }

    private void djnz() {
        int local_B = getBC() & msb;
        local_B = local_B - 256; // ( 1 * 2**8) - saves a shift >> 8
        setBC((getBC() & lsb) | (local_B & msb));
        if (local_B != 0) {
            tStates = tStates + config.OPCODE_T_STATES2[0x10];
            relativeJump();
        } else {
            incPC();
        }
    }

    private void jp(boolean cc) {
        if (cc)
            reg_PC = ram.readWord(reg_PC);
        else
            inc2PC();
    }

    private void jp() {
        reg_PC = ram.readWord(reg_PC);
    }

    private void ret(boolean cc, int instruction) {
        if (cc) {
            reg_PC = ram.readWord(reg_SP);
            inc2SP();
            tStates = tStates + config.OPCODE_T_STATES2[instruction];
        }
    }

    private void ret() {
        reg_PC = ram.readWord(reg_SP);
        inc2SP();
    }

    private void retn() {
        reg_PC = ram.readWord(reg_SP);
        inc2SP();
        IFF1 = IFF2;
    }

    private void reti() {
        reg_PC = ram.readWord(reg_SP);
        inc2SP();
    }

    private void call(boolean cc, int instruction) {
        if (cc) {
            call();
            tStates = tStates + config.OPCODE_T_STATES2[instruction];
        } else {
            inc2PC();
        }
    }

    private void call() {
        int destination = ram.readWord(reg_PC);
        inc2PC();
        dec2SP();
        ram.writeWord(reg_SP, reg_PC);
        reg_PC = destination;
    }

    private void rst(int code) {
        dec2SP();
        ram.writeWord(reg_SP, reg_PC);
        switch (code) {
            case 0: {
                reg_PC = 0x0000;
                break;
            }
            case 1: {
                reg_PC = 0x0008;
                break;
            }
            case 2: {
                reg_PC = 0x0010;
                break;
            }
            case 3: {
                reg_PC = 0x0018;
                break;
            }
            case 4: {
                reg_PC = 0x0020;
                break;
            }
            case 5: {
                reg_PC = 0x0028;
                break;
            }
            case 6: {
                reg_PC = 0x0030;
                break;
            }
            default: {
                reg_PC = 0x0038;
                break;
            }
        }
    }

    /*
     * Interrupt handling
     */
    private void DI() {
        IFF1 = false;
        EIDIFlag = true;
    }

    private void EI() {
        IFF1 = true;
        EIDIFlag = true;
    }

    /*
     * IO port handling
     */

    /* IN A,(NN) */
    private void inAN() {
        reg_A = io.IORead(ram.readByte(reg_PC));
        incPC();
        reg_R++;
    }

    /* OUT (NN),A */
    private void outNA() {
        io.IOWrite(ram.readByte(reg_PC), reg_A);
        incPC();
        reg_R++;
    }

    /* IN rr,(c) */
    private void inC(int reg) {
        int temp = io.IORead(getBC());
        // set8BitRegister( temp, reg );
        switch (reg) {
            case 0: {
                reg_B = temp;
                break;
            } // B
            case 1: {
                reg_C = temp;
                break;
            } // C
            case 2: {
                reg_D = temp;
                break;
            } // D
            case 3: {
                reg_E = temp;
                break;
            } // E
            case 4: {
                reg_H = temp;
                break;
            } // H
            case 5: {
                reg_L = temp;
                break;
            } // L
            case 7: {
                reg_A = temp;
                break;
            } // A
            case 6: {
                // Does nothing, just affects flags
                break;
            }
        }
        if ((temp & 0x0080) == 0)
            resetS();
        else
            setS();
        if (temp == 0)
            setZ();
        else
            resetZ();
        if (PARITY_TABLE[temp])
            setPV();
        else
            resetPV();
        resetN();
        resetH();
    }

    /* OUT (rr),c */
    private void outC(int reg) {
        io.IOWrite(getBC(), get8BitRegisterForIO(reg));
    }

    /*
     * bit manipulation
     */

    private void testBit(int value, int bit) {
        //
        resetS();
        set3((value & 0x08) != 0);
        set5((value & 0x20) != 0);

        switch (bit) {
            case 0: {
                value = value & setBit0;
                break;
            }
            case 1: {
                value = value & setBit1;
                break;
            }
            case 2: {
                value = value & setBit2;
                break;
            }
            case 3: {
                value = value & setBit3;
                break;
            }
            case 4: {
                value = value & setBit4;
                break;
            }
            case 5: {
                value = value & setBit5;
                break;
            }
            case 6: {
                value = value & setBit6;
                break;
            }
            default: {
                value = value & setBit7;
                setS(value != 0);
                break;
            }
        }
        setZ(0 == value);
        setPV(0 == value);
        resetN();
        setH();
    }

    private void testBitInMemory(int bit) {
        testBitGeneric(bit, ram.readByte(getHL()));
    }

    private void testBitGeneric(int bit, int value) {
        int v = value;
        resetS();
        switch (bit) {
            case 0: {
                v = v & setBit0;
                break;
            }
            case 1: {
                v = v & setBit1;
                break;
            }
            case 2: {
                v = v & setBit2;
                break;
            }
            case 3: {
                v = v & setBit3;
                break;
            }
            case 4: {
                v = v & setBit4;
                break;
            }
            case 5: {
                v = v & setBit5;
                break;
            }
            case 6: {
                v = v & setBit6;
                break;
            }
            default: {
                v = v & setBit7;
                setS(v != 0);
                break;
            }
        }
        setZ(0 == v);
        setPV(0 == v);
        resetN();
        setH();
    }

    /*
     * Increment / decrement repeat type instructions
     */
    /* loads */
    private void LDI() {
        reg_R++;
        int flags = reg_F;
        int value = ram.readByte(getHL());
        ram.writeByte(getDE(), value);
        setDE(ALU16BitInc(getDE()));
        setHL(ALU16BitInc(getHL()));
        setBC(ALU16BitDec(getBC()));
        reg_F = flags;
        resetH();
        resetN();
        setPV(getBC() != 0);
        int temp = value + reg_A;
        if ((temp & 0x02) == 0)
            reset5();
        else
            set5();
        if ((temp & 0x08) == 0)
            reset3();
        else
            set3();
    }

    private void LDIR() {
        blockMove = true;
        while (blockMove) {
            LDI();
            blockMove = getBC() != 0;
            if (blockMove) tStates = tStates + config.OPCODE_ED_STATES[0xB0] + config.OPCODE_ED_STATES2[0xB0];
        }
    }

    private void LDD() {
        reg_R++;
        int value = ram.readByte(getHL());
        ram.writeByte(getDE(), value);
        //
        setDE(ALU16BitDec(getDE()));
        setHL(ALU16BitDec(getHL()));
        setBC(ALU16BitDec(getBC()));
        resetH();
        resetN();
        setPV(getBC() != 0);
        int temp = reg_A + value;
        if ((temp & 0x02) == 0)
            reset5();
        else
            set5();
        if ((temp & 0x08) == 0)
            reset3();
        else
            set3();
    }

    private void LDDR() {
        blockMove = true;
        while (blockMove) {
            LDD();
            blockMove = getBC() != 0;
            if (blockMove) tStates = tStates + config.OPCODE_ED_STATES[0xB8] + config.OPCODE_ED_STATES2[0xB8];
        }
    }

    /*
     * block compares
     */
    private void CPI() {
        reg_R++;
        int value = ram.readByte(getHL());
        int result = (reg_A - value) & lsb;
        setHL(ALU16BitInc(getHL()));
        setBC(ALU16BitDec(getBC()));
        //
        if ((result & 0x80) == 0)
            resetS();
        else
            setS();
        if (result == 0)
            setZ();
        else
            resetZ();
        setHalfCarryFlagSub(reg_A, value);
        setPV(getBC() != 0);
        setN();
        //
        if (getH())
            result--;
        if ((result & 0x00002) == 0)
            reset5();
        else
            set5();
        if ((result & 0x00008) == 0)
            reset3();
        else
            set3();
    }

    private void CPIR() {
        CPI();
        if (!getZ() && (getBC() != 0)) {
            dec2PC();
            tStates = tStates + config.OPCODE_ED_STATES2[0xB1];        
        }
    }

    private void CPD() {
        reg_R++;
        int value = ram.readByte(getHL());
        int result = (reg_A - value) & lsb;
        setHL(ALU16BitDec(getHL()));
        setBC(ALU16BitDec(getBC()));
        //
        if ((result & 0x80) == 0)
            resetS();
        else
            setS();
        if (result == 0)
            setZ();
        else
            resetZ();
        setHalfCarryFlagSub(reg_A, value);
        setPV(getBC() != 0);
        setN();
        //
        if (getH())
            result--;
        if ((result & 0x02) == 0)
            reset5();
        else
            set5();
        if ((result & 0x08) == 0)
            reset3();
        else
            set3();
    }

    private void CPDR() {
        CPD();
        if (!getZ() && (getBC() != 0)) {
            dec2PC();
            tStates = tStates + config.OPCODE_ED_STATES2[0xB9];
        }
    }

    /* block IO */
    private void INI() {
        ram.writeByte(getHL(), io.IORead(reg_C));
        reg_B = (reg_B - 1) & lsb;
        setHL(ALU16BitInc(getHL()));
        setZ(reg_B == 0);
        setN();
    }

    private void INIR() {
        INI();
        if (!getZ()) {
            dec2PC();
            tStates = tStates + config.OPCODE_ED_STATES2[0xB2];
        }
    }

    private void IND() {
        ram.writeByte(getHL(), io.IORead(reg_C));
        reg_B = (reg_B - 1) & lsb;
        setHL(ALU16BitDec(getHL()));
        setZ(reg_B == 0);
        setN();
    }

    private void INDR() {
        IND();
        if (!getZ()) {
            dec2PC();
            tStates = tStates + config.OPCODE_ED_STATES2[0xBA];
        }
    }

    private void OUTI() {
        io.IOWrite(reg_C, ram.readByte(getHL()));
        reg_R++;
        reg_B = (reg_B - 1) & lsb;
        setHL(ALU16BitInc(getHL()));
        setZ(reg_B == 0);
        setN();
    }

    private void OTIR() {
        OUTI();
        if (!getZ()) {
            dec2PC();
            tStates = tStates + config.OPCODE_ED_STATES2[0xB3];
        }
    }

    private void OUTD() {
        io.IOWrite(reg_C, ram.readByte(getHL()));
        reg_R++;
        reg_B = (reg_B - 1) & lsb;
        setHL(ALU16BitDec(getHL()));
        setZ(reg_B == 0);
        setN();
    }

    private void OTDR() {
        OUTD();
        if (!getZ()) {
            dec2PC();
            tStates = tStates + config.OPCODE_ED_STATES2[0xBB];
        }
    }

    /*
     * extended 16 bit loads for ED instructions
     */
    private void LDRegnnnnInd16Bit(int regCode) {
        int address = ram.readWord(reg_PC);
        int data = ram.readWord(address);
        set16BitRegister(data, regCode);
        inc2PC();
    }

    private void LDnnnnRegInd16Bit(int regCode) {
        int address = ram.readWord(reg_PC);
        ram.writeWord(address, get16BitRegister(regCode));
        inc2PC();
    }

    /*
     * odds & ends
     */

    private void IM(int mode) {
        // interruptMode = mode;
    }

    /*
     * special I reg loads
     */
    private void LDAI() {
        reg_A = reg_I;
        setS((reg_A & flag_S) != 0);
        setZ(reg_A == 0);
        resetH();
        resetN();
        setPV(IFF2);
        setUnusedFlags(reg_A);
    }

    private void LDIA() {
        reg_I = reg_A;
    }

    /*
     * special R reg loads
     */

    private void LDAR() {
        reg_A = reg_R & 0x7F;
        resetS();
        setZ(reg_A == 0);
        resetH();
        resetN();
        setPV(IFF2);
        setUnusedFlags(reg_A);
    }

    private void LDRA() {
        reg_R = reg_A;
    }

    //
    //
    // Index IX & IY register special instructions
    //
    //

    /**
     * Get the index value, make signed as its two's compliment.
     *
     * @return The index register offset value in the range -128..+127
     */
    private int getIndexOffset() {
        reg_R++;
        int index = ram.readByte(reg_PC);
        incPC();
        if (index > 0x007f)
            return (index - 256);
        else
            return index;
    }

    private int getIndexAddress() {
        return (reg_index + getIndexOffset()) & lsw;
    }

    /*
     * Support for 8 bit index register manipulation (IX as IXH IXL)
     */
    private int getIndexAddressUndocumented(int reg) {
        switch (reg) {
            case 4: {
                return ((reg_index & msb) >> 8);
            } // IXH
            case 5: {
                return (reg_index & lsb);
            } // IXL
            default: {
                reg_R++;
                return ram.readByte((reg_index + getIndexOffset()) & lsw);
            } // (index+dd)
        }
    }

    /*
     * Support for 8 bit index register manipulation (IX as IXH IXL)
     */
    private void setIndexAddressUndocumented(int value, int reg) {
        switch (reg) {
            case 4: {
                reg_index = reg_index & lsb;
                reg_index = reg_index | (value << 8);
                break;
            } // IXH
            case 5: {
                reg_index = reg_index & msb;
                reg_index = reg_index | value;
                break;
            } // IXL
            default: {
                ram.writeByte((getIndexAddress()), value);
                break;
            } // (index+dd)
        }
    }

    /*
     * return an 8 bit register based on its code 000 -> 111
     */
    private int get8BitRegisterIndexed(int reg) {
        switch (reg) {
            case 4: {
                return reg_H;
            } // H
            case 5: {
                return reg_L;
            } // L
            case 7: {
                return reg_A;
            } // A
            default: {
                reg_R++;
                return ram.readByte(getIndexAddress());
            } // (index+dd)
        }
    }

    /* inc / dec (index+dd) */
    private void incIndex() {
        int address = getIndexAddress();
        int data = ALU8BitInc(ram.readByte(address));
        reg_R++;
        ram.writeByte(address, data);
    }

    private void decIndex() {
        int address = getIndexAddress();
        int data = ALU8BitDec(ram.readByte(address));
        reg_R++;
        ram.writeByte(address, data);
    }

    /* index register swap */
    private void EXSPIndex() {
        int temp = reg_index;
        reg_index = ram.readWord(reg_SP);
        inc2SP();
        dec2SP();
        ram.writeWord(reg_SP, temp);
    }

    /* indexed CB bit twiddling */
    private void testIndexBit(int bit) {
        reg_R++;
        int address = getIndexAddress();
        int temp = ram.readByte(address);

        // check the bit position
        testBitGeneric(bit, temp);
    }

    private void bitIndexSet(int bit) {
        reg_R++;
        int address = getIndexAddress();
        int temp = ram.readByte(address);
        switch (bit) {
            case 0: {
                temp = temp | setBit0;
                break;
            }
            case 1: {
                temp = temp | setBit1;
                break;
            }
            case 2: {
                temp = temp | setBit2;
                break;
            }
            case 3: {
                temp = temp | setBit3;
                break;
            }
            case 4: {
                temp = temp | setBit4;
                break;
            }
            case 5: {
                temp = temp | setBit5;
                break;
            }
            case 6: {
                temp = temp | setBit6;
                break;
            }
            default:
                temp = temp | setBit7;
        }
        ram.writeByte(address, temp);
    }

    private void bitIndexReset(int bit) {
        reg_R++;
        int address = getIndexAddress();
        int temp = ram.readByte(address);
        switch (bit) {
            case 0: {
                temp = temp & resetBit0;
                break;
            }
            case 1: {
                temp = temp & resetBit1;
                break;
            }
            case 2: {
                temp = temp & resetBit2;
                break;
            }
            case 3: {
                temp = temp & resetBit3;
                break;
            }
            case 4: {
                temp = temp & resetBit4;
                break;
            }
            case 5: {
                temp = temp & resetBit5;
                break;
            }
            case 6: {
                temp = temp & resetBit6;
                break;
            }
            default:
                temp = temp & resetBit7;
        }
        ram.writeByte(address, temp);
    }

    /* LD (ix+dd),nn */
    private void loadIndex8BitImmediate() {
        reg_R++;
        int address = getIndexAddress();
        int data = ram.readByte(reg_PC);
        incPC();
        ram.writeByte(address, data);
    }

    /**
     * Get the processor major CPU version number
     *
     * @return major revision number
     */
    public String getMajorVersion() {
        return "1";
    }

    /**
     * Get the processor major CPU minor number
     *
     * @return minor revision number
     */
    public String getMinorVersion() {
        return "2";
    }

    /**
     * Get the processor major CPU patch number
     *
     * @return patch number
     */
    public String getPatchVersion() {
        return "0";
    }

    /**
     * Get the CPU name string
     *
     * @return name string
     */
    public String getName() {
        return "Z80A_NMOS";
    }

    /**
     * Return the full CPU name
     *
     * @return name string
     */
    public String toString() {
        return getName() + " Revision " + getMajorVersion() + "." + getMinorVersion() + "." + getPatchVersion();
    }

}
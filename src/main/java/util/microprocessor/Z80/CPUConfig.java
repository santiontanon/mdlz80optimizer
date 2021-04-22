/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util.microprocessor.Z80;

import cl.MDLConfig;
import code.CPUOpSpec;

/**
 *
 * @author santi
 * 
 * In the original Z80Processor project ( https://github.com/codesqueak/Z80Processor ),
 * timings for CPU instructions were hardcoded. However ,MDL needs to simulate the Z80 
 * setup used in different machines (Spectrum, MSX, Amstrad, etc.), which have different
 * timings. So, I have separated these variables into this class, which gets them initialized
 * from the CPU op specs used by MDL (so that all the info is centralized in one single place).
 * If you want to separate this Z80 simulator out of MDL for your own uses, you can simply
 * specify the value of the OPCODE_T_STATES, OPCODE_T_STATES2, etc. arrays by hand, like
 * done in the original Z80Processor project. 
 * 
 * Note, however, that I have added a couple more arrays (OPCODE_T_STATES2 and
 * OPCODE_ED_STATES2), to handle the instructions with variable times, which were
 * previously hardcoded in the code.
 * 
 */
public class CPUConfig {
    MDLConfig config = null;
    
    // Timing for all instructions:
    byte[] OPCODE_T_STATES;
    byte[] OPCODE_T_STATES2;    // for instructions that take different time when jumping    
    byte[] OPCODE_CB_STATES;
    byte[] OPCODE_DD_FD_STATES;
    byte[] OPCODE_ED_STATES;
    byte[] OPCODE_ED_STATES2;    // for instructions that take different time when jumping    
    byte[] OPCODE_INDEXED_CB_STATES;

    
    public CPUConfig(MDLConfig a_config) {
        config = a_config;

        OPCODE_T_STATES = new byte[256];
        OPCODE_T_STATES2 = new byte[256];
        OPCODE_CB_STATES = new byte[256];
        OPCODE_INDEXED_CB_STATES = new byte[256];
        OPCODE_DD_FD_STATES = new byte[256];
        OPCODE_ED_STATES = new byte[256];
        OPCODE_ED_STATES2 = new byte[256];
        for(int i = 0;i<256;i++) {
            OPCODE_T_STATES[i] = -1;
            OPCODE_T_STATES2[i] = -1;
            OPCODE_CB_STATES[i] = -1;
            OPCODE_INDEXED_CB_STATES[i] = -1;
            OPCODE_DD_FD_STATES[i] = -1;
            OPCODE_ED_STATES[i] = -1;
            OPCODE_ED_STATES2[i] = -1;
        }
        
        for(CPUOpSpec spec:config.opParser.getOpSpecs()) {
            // generate all instantiations (except for "n" and "nn" arguments, which are just set to 0),
            // to create a byte to CPUOp table:
            int buffer[] = {0, 0, 0, 0};
            allSpecInstantiations(spec, buffer, 0, 0);
        }        
    }
    
    
    private void allSpecInstantiations(CPUOpSpec spec, int buffer[], int byteOffset, int nextArg)
    {
        if (byteOffset >= spec.bytesRepresentation.size()) {
            // we are done:
            switch (buffer[0]) {
                case 0xcb:
                    if (OPCODE_CB_STATES[buffer[1]] == -1) {
                        OPCODE_T_STATES[buffer[0]] = 0;
                        OPCODE_CB_STATES[buffer[1]] = (byte)spec.times[0];
                    }
                    break;
                case 0xdd:
                case 0xfd:
                    if (buffer[1] == 0xcb) {
                        if (OPCODE_INDEXED_CB_STATES[buffer[3]] == -1) {
                            OPCODE_T_STATES[buffer[0]] = 0;
                            OPCODE_DD_FD_STATES[buffer[1]] = 0;
                            OPCODE_INDEXED_CB_STATES[buffer[3]] = (byte)spec.times[0];
                        }
                    } else {
                        if (OPCODE_DD_FD_STATES[buffer[1]] == -1) {
                            OPCODE_T_STATES[buffer[0]] = 0;
                            OPCODE_DD_FD_STATES[buffer[1]] = (byte)spec.times[0];
                        }
                    }
                    break;
                case 0xed:
                    if (OPCODE_ED_STATES[buffer[1]] == -1) {
                        OPCODE_T_STATES[buffer[0]] = 0;
                        OPCODE_ED_STATES[buffer[1]] = (byte)spec.times[0];
                        if (spec.times.length > 1) {
                            OPCODE_ED_STATES[buffer[1]] = (byte)(spec.times[1]);
                            OPCODE_ED_STATES2[buffer[1]] = (byte)(spec.times[0] - OPCODE_ED_STATES[buffer[1]]);
                        }
                    }
                    break;
                default:
                    if (OPCODE_T_STATES[buffer[0]] == -1) {
                        OPCODE_T_STATES[buffer[0]] = (byte)spec.times[0];
                        if (spec.times.length > 1) {
                            OPCODE_T_STATES[buffer[0]] = (byte)(spec.times[1]);
                            OPCODE_T_STATES2[buffer[0]] = (byte)(spec.times[0] - OPCODE_T_STATES[buffer[0]]);
                        }
                    }
                    break;
            }
            return;
        }
        
        String v[] = spec.bytesRepresentation.get(byteOffset);
        int baseByte = config.tokenizer.parseHex(v[0]);
        buffer[byteOffset] = baseByte;
        
        if (v[1].equals("")) {
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("o")) {
            // jr/djnz offset:
            buffer[byteOffset] = 0;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg+1);
        } else if (v[1].equals("n")) {
            // 8 bit argument
            buffer[byteOffset] = 0;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg+1);
        } else if (v[1].equals("nn")) {
            // 16 bit argument
            buffer[byteOffset] = 0;
            buffer[byteOffset+1] = 0;
            allSpecInstantiations(spec, buffer, byteOffset+2, nextArg+1);
        } else if (v[1].equals("+0")) {
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+1")) {
            buffer[byteOffset] += 1;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+2")) {
            buffer[byteOffset] += 2;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+3")) {
            buffer[byteOffset] += 3;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+4")) {
            buffer[byteOffset] += 4;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+5")) {
            buffer[byteOffset] += 5;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+6")) {
            buffer[byteOffset] += 6;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);
        } else if (v[1].equals("+7")) {
            buffer[byteOffset] += 7;
            allSpecInstantiations(spec, buffer, byteOffset+1, nextArg);

        } else if (v[1].equals("+r") ||
                   v[1].equals("+p") ||
                   v[1].equals("+q")) {
            // register (which is the last argument of the op):
            for(int i = 0;i<8;i++) {
                buffer[byteOffset] += i;
                allSpecInstantiations(spec, buffer, byteOffset+1, spec.args.size());
                buffer[byteOffset] -= i;
            }

        } else if (v[1].equals("+8*p") ||
                   v[1].equals("+8*q")) {
            // register (which is the last argument of the op):
            for(int i = 0;i<8;i++) {
                buffer[byteOffset] += i*8;
                allSpecInstantiations(spec, buffer, byteOffset+1, spec.args.size());
                buffer[byteOffset] -= i*8;
            }
            
        } else if (v[1].equals("+8*b")) {
            // register (which is the first argument of the op):
            for(int i = 0;i<8;i++) {
                buffer[byteOffset] += i*8;
                allSpecInstantiations(spec, buffer, byteOffset+1, 1);
                buffer[byteOffset] -= i*8;
            }

        } else if (v[1].equals("+8*b+r")) {
            // register (which is the last argument of the op):
            for(int i = 0;i<8;i++) {
                buffer[byteOffset] += i*8;
                for(int j = 0;j<8;j++) {
                    buffer[byteOffset] += j;
                    allSpecInstantiations(spec, buffer, byteOffset+1, 2);
                    buffer[byteOffset] -= j;
                }
                buffer[byteOffset] -= i*8;
            }
        }        
    }
    
}

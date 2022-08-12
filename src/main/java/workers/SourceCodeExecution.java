/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.SourceFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import util.microprocessor.PlainZ80IO;
import util.microprocessor.TrackingZ80Memory;
import util.microprocessor.Z80.CPUConfig;
import util.microprocessor.Z80.CPUConstants;
import util.microprocessor.Z80.CPUConstants.RegisterNames;
import util.microprocessor.Z80.Z80Core;

/**
 *
 * @author santi
 */
public class SourceCodeExecution implements MDLWorker {

    MDLConfig config = null;
    String startAddressString = null;
    String stepsString = null;
    boolean trace = false;

    
    public SourceCodeExecution(MDLConfig a_config)
    {
        config = a_config;
    }

    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-e <address> <steps>```: executes the source code starting at <address> for <steps> CPU cycles, and displays the changed registers and memory.\n";
    }

    @Override
    public String simpleDocString() {
        return "";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-e") && flags.size()>=3) {
            flags.remove(0);
            startAddressString = flags.remove(0);
            stepsString = flags.remove(0);
            return true;
        } else if (flags.get(0).equals("-e:trace")) {
            flags.remove(0);
            trace = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean triggered() {
        return startAddressString != null;
    }

    @Override
    public boolean work(CodeBase code) 
    {
        TrackingZ80Memory z80Memory = new TrackingZ80Memory(null);
        Z80Core z80 = new Z80Core(z80Memory, new PlainZ80IO(), new CPUConfig(config));
        z80Memory.setCPU(z80);
        z80.reset();
        
        // assemble and copy program to z80 memory:
        HashMap<Integer, CodeStatement> instructions = new HashMap<>();
        SourceFile main = code.outputs.get(0).main;
        BinaryGenerator generator = new BinaryGenerator(config);
        List<BinaryGenerator.StatementBinaryEffect> statementBytes = new ArrayList<>();        
        generator.generateStatementBytes(main, code, statementBytes);
        for(BinaryGenerator.StatementBinaryEffect effect:statementBytes) {
            Integer address = effect.s.getAddress(code);
            instructions.put(address, effect.s);
            for(int i = 0;i<effect.bytes.length;i++) {
                int b = effect.bytes[i];
                if (b < 0) b += 256;
                z80Memory.writeByte(address + i, b);
            }
        }
        z80Memory.clearMemoryAccesses();
        
        List<String> tokens = config.tokenizer.tokenize(startAddressString);
        Expression exp = config.expressionParser.parse(tokens, null, null, code);
        int startAddress = exp.evaluateToInteger(null, code, false);
        tokens = config.tokenizer.tokenize(stepsString);
        exp = config.expressionParser.parse(tokens, null, null, code);
        int steps = exp.evaluateToInteger(null, code, false);
        
        // Set program counter:
        z80.setProgramCounter(startAddress);
        
        config.debug("SourceCodeExecution: start address: " + startAddress);
        config.debug("SourceCodeExecution: steps: " + steps);
        
        // Execute!
        try {
            while(z80.getTStates() < steps) {
                if (trace) {
                    int address = z80.getProgramCounter();
                    CodeStatement s = instructions.get(address);
                    if (s == null) {
                        config.warn("SourceCodeExecution: execution move away from provided source code!");
                    } else {
                        config.info("  executing: " + s.toString());
                    }
                }
                z80.executeOneInstruction();
            }
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        
        // Print changes:
        config.info("Execution result:");
        for(RegisterNames reg:CPUConstants.eightBitRegisters) {
            int v = z80.getRegisterValue(reg);
            if (v != 0) {
                config.info("  " + CPUConstants.registerName(reg) + ": " + v + " (" + config.tokenizer.toHexByte(v, config.hexStyle) + ")");
            }
        }
        for(int address:z80Memory.getMemoryWrites()) {
            config.info("  (" + config.tokenizer.toHexWord(address, config.hexStyle) + ") = " + 
                    z80Memory.readByte(address) + 
                    " (" + config.tokenizer.toHexByte(z80Memory.readByte(address), config.hexStyle) + ")");
        }
                
        return true;
    }
    
}
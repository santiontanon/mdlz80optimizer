/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package workers;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.OutputBinary;
import code.SourceConstant;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import util.ListOutputStream;

/**
 *
 * @author santi
 */
public class TapGenerator implements MDLWorker {

    
    MDLConfig config = null;

    String outputFileName = null;
    String executionStartAddressString = null;
    String programName = "PROG";

    public TapGenerator(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-tap <execution start address> <program name> <filename>```: generates a .tap file, as expected by ZX spectrum emulators. " +
               "```<execution start address>''' is the entry point of the program. It can be any expression MDL recognizes in source code, e.g., a constant like ```#a600```, a label, like ```CodeStart```, or an expression like ```myLabel+10```. " +
               "```<program name>``` is the name you want to be displayed when the program loads, e.g. ```MYGAME``` (only the first 10 characters will be displayed).\n";
    }

    @Override
    public String simpleDocString() {
        return "";
    }

    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-tap") && flags.size()>=4) {
            flags.remove(0);
            executionStartAddressString = flags.remove(0);
            programName = flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        }
        return false;
    }

    @Override
    public boolean triggered() {
        return outputFileName != null;
    }

    @Override
    public boolean work(CodeBase code) {
        config.debug("Executing "+this.getClass().getSimpleName()+" worker...");
        if (code.outputs.size() > 1) {
            config.warn("Code base has more than one output binary defined in the sources. " +
                        "MDL cannot determine which of them to use for generating a .tap file, " +
                        "hence, the first one will be used.");
        }

        // Assemble the source code to binary:
        BinaryGenerator gen = new BinaryGenerator(config);
        ListOutputStream los = new ListOutputStream();
        OutputBinary output = code.outputs.get(0);
        try {
            if (!gen.writeBytes(output.main, code, los, 0, true)) {
                return false;
            }
        } catch (Exception ex) {
            config.error("Exception: " + ex.getMessage());
            config.error("Stack Trace: " + Arrays.toString(ex.getStackTrace()));
            return false;
        }
        List<Integer> binary = los.getData();
        
        // Obtain the execution start address:
        List<String> tokens = config.tokenizer.tokenize(executionStartAddressString);
        Expression executionStartAddressExp = config.expressionParser.parse(tokens, null, null, code);
        if (executionStartAddressExp == null) {
            config.error("Cannot parse execution start address expression " + executionStartAddressString);
            return false;
        }
        Integer executionStartAddress = executionStartAddressExp.evaluateToInteger(null, code, true);
        if (executionStartAddress == null) {
            config.error("Cannot evaluate execution start address expression " + executionStartAddressString);
            return false;
        }
        
        // Generate the .tap blocks:
        int binaryStartAddress = output.main.getStartAddress(code);
        // Code binary:
        List<Integer> codeBlock = generateTapBlock(binary, 0xff);
        // Basic loader:
        List<Integer> BASICLoaderBlock = generateBASICLoaderBlock(0x5e00);
        
        // Assembler loader:
        ListOutputStream assemblerLoaderOut = new ListOutputStream();
        try {
            BinaryGenerator bg = new BinaryGenerator(config);
            MDLConfig loaderConfig = new MDLConfig();
            CodeBase loaderCode = new CodeBase(loaderConfig);
            if (!loaderConfig.parseArgs("data/zxspectrum-tap-loader.asm") ||
                !loaderConfig.codeBaseParser.parseMainSourceFiles(loaderConfig.inputFiles, loaderCode)) {
                config.error("Problem loading 'data/zxspectrum-tap-loader.asm' file to generate the.tap loader. This is probably a bug, please report!");
                return false;
            }
            // Define symbols:
            loaderCode.addSymbol("EXECUTION_START",
                    new SourceConstant("EXECUTION_START", "EXECUTION_START",
                            Expression.constantExpression(executionStartAddress, config), null, config));
            loaderCode.addSymbol("BINARY_START",
                    new SourceConstant("BINARY_START", "BINARY_START",
                            Expression.constantExpression(binaryStartAddress, config), null, config));
            loaderCode.addSymbol("BINARY_LENGTH",
                    new SourceConstant("BINARY_LENGTH", "BINARY_LENGTH",
                            Expression.constantExpression(codeBlock.size()-4, config), null, config));
            bg.writeBytes(loaderCode.outputs.get(0).main, loaderCode, assemblerLoaderOut, 0, true);
        } catch (Exception ex) {
            config.error("Exception: " + ex.getMessage());
            config.error("Stack Trace: " + Arrays.toString(ex.getStackTrace()));
            return false;
        }
        List<Integer> asmLoaderBlock = generateTapBlock(assemblerLoaderOut.getData(), 0xff);
        
        
        // .tap headers:
        List<Integer> header1 = generateTapHeader(programName,
                BASICLoaderBlock.size()-4, 10, BASICLoaderBlock.size()-4, 0);
        List<Integer> header2 = generateTapHeader(programName,
                asmLoaderBlock.size()-4, 0x5e00, 0, 3);
        
        // Write .tap to disk:
        try (FileOutputStream os = new FileOutputStream(outputFileName)) {
            config.debug(".tap BASIC header: " + header1.size() + " bytes");
            for(int v:header1) os.write(v);
            config.debug(".tap BASIC loader: " + BASICLoaderBlock.size() + " bytes");
            for(int v:BASICLoaderBlock) os.write(v);
            config.debug(".tap assembler header: " + header2.size() + " bytes");
            for(int v:header2) os.write(v);
            config.debug(".tap assembler loader: " + asmLoaderBlock.size() + " bytes");
            for(int v:asmLoaderBlock) os.write(v);
            config.debug(".tap code: " + codeBlock.size() + " bytes");
            for(int v:codeBlock) os.write(v);
            os.flush();
        } catch (Exception e) {
            config.error("Cannot write to file " + outputFileName + ": " + e);
            config.error(Arrays.toString(e.getStackTrace()));
            return false;
        }
        
        return true;
    }
    
    
    public List<Integer> generateTapBlock(List<Integer> data, int headerIndicator)
    {
        List<Integer> block = new ArrayList<>();
        int length = data.size() + 2;
        block.add(length%256);
        block.add(length/256);
        block.add(headerIndicator);
        block.addAll(data);
        int checksum = headerIndicator;
        for(int v:data) {
            checksum ^= v;
        }
        block.add(checksum & 0xff);
        return block;
    }
    
    
    public List<Integer> generateTapHeader(String name, int binarySize, int startAddress, int parameter2, int flag)
    {
        List<Integer> data = new ArrayList<>();
                
        data.add(flag);  // flag
        
        // File name:
        for(int i = 0;i<10;i++) {
            int c = ' ';
            if (name.length() > i) {
                c = name.charAt(i);
            }
            data.add((int)c);
        }
        
        // Size of the program that comes in the next block:
        data.add(binarySize%256);
        data.add(binarySize/256);
        
        // Parameter 1:
        data.add(startAddress%256);
        data.add(startAddress/256);

        // Parameter 2:
        data.add(parameter2%256);
        data.add(parameter2/256);
        
        if (data.size() != 17) {
            config.error("generateTapHeader generated the wrong amount of data! " + data.size() + ", and it should have been 17. This is a bug, please report.");
        }
        
        return generateTapBlock(data, 0);
    }

    private List<Integer> generateBASICLoaderBlock(int executionStartAddress) 
    {
        List<Integer> data = new ArrayList<>();
        data.add(0); // flag
        
        data.add(10); // BASIC line number
        data.add(32); // BASIC line length
        data.add(0);
        
        // CLEAR VAL #5e00
        data.add(0xfd);  // CLEAR
        data.add(0xb0);  // VAL
        data.add((int)'\"');
        String numberString = "" + (0x5e00-1);
        for(int i = 0;i<numberString.length();i++) {
            data.add((int)numberString.charAt(i));        
        }
        data.add((int)'\"');

        // CLS
        data.add((int)':');  
        data.add(0xfb);  // CLS

        // LOAD "" CODE
        data.add((int)':');  
        data.add(0xef);  // LOAD
        data.add((int)'\"');
        data.add((int)'\"');
        data.add(0xaf);  // CODE

        // RANDOMIZE USR VAL executionStartAddress
        data.add((int)':');
        data.add(0xf9);  // RANDOMIZE
        data.add(0xc0);  // USR
        data.add(0xb0);  // VAL
        data.add((int)'\"');
        numberString = "" + executionStartAddress;
        for(int i = 0;i<numberString.length();i++) {
            data.add((int)numberString.charAt(i));        
        }
        data.add((int)'\"');
        data.add(0x0d);
        
        return generateTapBlock(data, 0xff);
    }
}

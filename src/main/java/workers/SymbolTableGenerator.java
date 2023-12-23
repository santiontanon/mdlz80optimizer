/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cl.MDLConfig;
import code.CodeBase;
import code.CodeStatement;
import code.Expression;
import code.OutputBinary;
import code.SourceConstant;
import java.util.HashMap;

/**
 *
 * @author santi
 */
public class SymbolTableGenerator implements MDLWorker {
    
    public final int STANDARD_SYMBOL_FILE = 0;
    public final int SJASM_SYMBOL_FILE = 1;

    MDLConfig config = null;

    String outputFileName = null;
    public int format = STANDARD_SYMBOL_FILE;
    public boolean includeConstants = false;

    public SymbolTableGenerator(MDLConfig a_config)
    {
        config = a_config;
    }
    

    @Override
    public String docString() {
        // This string has MD tags, so that I can easily generate the corresponding documentation in github with the 
        // hidden "-helpmd" flag:        
        return "- ```-st <output file>```: to output the symbol table.\n" +
               "- ```-st-constants```: includes constants, in addition to labels, in the output symbol table.\n" +
               "- ```-st-sjasm```: generates the symbol table like the symbol section of the sjasm .lst format. " +
               "This is useful to load symbols in emulators, like Emulicious, that do not support standard symbol table files.\n";
    }

    
    @Override
    public String simpleDocString() {
        return "";
    }

    
    @Override
    public boolean parseFlag(List<String> flags) {
        if (flags.get(0).equals("-st") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            return true;
        }
        if (flags.get(0).equals("-st-constants")) {
            flags.remove(0);
            includeConstants = true;
            return true;
        }
        if (flags.get(0).equals("-st-sjasm") && flags.size()>=2) {
            flags.remove(0);
            outputFileName = flags.remove(0);
            format = SJASM_SYMBOL_FILE;
            return true;
        }
        return false;
    }

    @Override
    public boolean work(CodeBase code) {

        if (outputFileName != null) {
            config.debug("Executing "+this.getClass().getSimpleName()+" worker...");

            try (FileWriter fw = new FileWriter(outputFileName)) {
                switch(format) {
                    case SJASM_SYMBOL_FILE:
                        fw.write(sjasmSymbolTableString(code));
                        break;
                    case STANDARD_SYMBOL_FILE:
                        fw.write(symbolTableString(code));
                        break;
                }
                fw.flush();
            } catch (Exception e) {
                config.error("Cannot write to file " + outputFileName + ": " + e);
                return false;
            }
        }

        return true;
    }


    public String symbolTableString(CodeBase code)
    {
        List<String> sortedSymbols = new ArrayList<>();
        sortedSymbols.addAll(code.getSymbols());
        Collections.sort(sortedSymbols);
        StringBuilder sb = new StringBuilder();
        for(String name:sortedSymbols) {
            SourceConstant symbol = code.getSymbol(name);
            if (symbol.exp == null) continue;
            if (symbol.exp.type == Expression.EXPRESSION_SYMBOL &&
                symbol.exp.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS) ||
                includeConstants) {
                sb.append(name);
                sb.append(": equ ");
                if (symbol.exp.isConstant()) {
                    sb.append(symbol.getValue(code, true));
                } else if (symbol.isLabel()) {
                    Object value = symbol.getValue(code, true);
                    if (value instanceof Integer) {
                        int int_value = (Integer)value;
                        if (int_value < 0x10000) {
                            if (config.hexStyleChanged) {
                                sb.append(config.tokenizer.toHexWord(int_value, config.hexStyle));
                            } else {
                                sb.append(config.tokenizer.toHexWord(int_value, MDLConfig.HEX_STYLE_0X));
                            }
                        } else {
                            if (config.hexStyleChanged) {
                                sb.append(config.tokenizer.toHexDWord(int_value, config.hexStyle));
                            } else {
                                sb.append(config.tokenizer.toHexDWord(int_value, MDLConfig.HEX_STYLE_0X));
                            }
                        }
                    } else {
                        sb.append(value);
                    }
                } else {
                    sb.append(symbol.getValue(code, true));
                    sb.append("  ; ");
                    sb.append(symbol.exp);
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }


    public String sjasmSymbolTableString(CodeBase code)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("      LABELS\n");
        sb.append("-------------------------------------------------\n");
        
        List<String> sortedSymbols = new ArrayList<>();
        sortedSymbols.addAll(code.getSymbols());
        HashMap<CodeStatement, Integer> map = statementToPageMap(code);
        for(String name:sortedSymbols) {
            SourceConstant symbol = code.getSymbol(name);
            if (symbol.exp == null) continue;
            if (symbol.exp.type == Expression.EXPRESSION_SYMBOL &&
                symbol.exp.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS) ||
                includeConstants) {
                // 00:0000009F X CHGET
                Object value = symbol.getValue(code, true);
                if (value instanceof Integer) {
                    String page = "00";
                    // See if we have a page annotation for it:
                    if (map.containsKey(symbol.definingStatement)) {
                        page = "" + map.get(symbol.definingStatement);
                        while(page.length() < 2) {
                            page = "0" + page;
                        }
                    }
                    sb.append(page);
                    sb.append(":0000");
                    int int_value = (Integer)value;
                    sb.append(config.tokenizer.toHex(int_value, 4));
                    sb.append("   ");
                    sb.append(symbol.name);
                    sb.append("\n");
                }
            }
        }        
        
        return sb.toString();
    }
    
    
    public HashMap<CodeStatement, Integer> statementToPageMap(CodeBase code)
    {
        HashMap<CodeStatement, Integer> map = new HashMap<>();
        for(OutputBinary output:code.outputs) {
            Integer currentPage = null;
            List<CodeStatement> l = output.main.linearizeStatements(code);
            for(CodeStatement s:l) {
                if (s.comment != null && s.comment.contains(config.PRAGMA_PAGE)) {
                    int idx = s.comment.indexOf(config.PRAGMA_PAGE) + config.PRAGMA_PAGE.length();
                    String tokens[] = s.comment.substring(idx).split(" ");
                    String pageString = null;
                    for(String token:tokens) {
                        token = token.strip();
                        if (!token.isEmpty()) {
                            pageString = token;
                            break;
                        }
                    }
                    if (pageString != null && config.tokenizer.isInteger(pageString)) {
                        currentPage = Integer.parseInt(pageString);
                    }
                }
                if (currentPage != null) {
                    map.put(s, currentPage);
                }
            }
        }
        return map;
    }


    @Override
    public boolean triggered() {
        return outputFileName != null;
    }
}
